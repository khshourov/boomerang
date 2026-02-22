package io.boomerang.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.boomerang.config.ServerConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocksDBLongTermTaskStoreTest {
  @TempDir Path tempDir;

  private RocksDBLongTermTaskStore store;
  private ServerConfig serverConfig;

  @BeforeEach
  void setUp() {
    serverConfig = mock(ServerConfig.class);
    when(serverConfig.getRocksDbPath()).thenReturn(tempDir.resolve("db").toString());
    store = new RocksDBLongTermTaskStore(serverConfig);
  }

  @AfterEach
  void tearDown() {
    if (store != null) {
      store.close();
    }
  }

  @Test
  void testSaveAndFindById() {
    TimerTask task = new TimerTask("task1", "client1", 10000, "payload".getBytes(), 0, () -> {});
    store.save(task);

    Optional<TimerTask> found = store.findById("task1");
    assertThat(found).isPresent();
    assertThat(found.get().getTaskId()).isEqualTo("task1");
    assertThat(found.get().getClientId()).isEqualTo("client1");
    assertThat(found.get().getExpirationMs()).isEqualTo(task.getExpirationMs());
    assertThat(found.get().getPayload()).isEqualTo("payload".getBytes());
  }

  @Test
  void testUpdateTask() {
    TimerTask task1 = new TimerTask("task1", "client1", 10000, "payload1".getBytes(), 0, () -> {});
    store.save(task1);

    TimerTask task1Updated =
        new TimerTask("task1", "client1", 20000, "payload2".getBytes(), 0, () -> {});
    store.save(task1Updated);

    Optional<TimerTask> found = store.findById("task1");
    assertThat(found).isPresent();
    assertThat(found.get().getPayload()).isEqualTo("payload2".getBytes());
    assertThat(found.get().getExpirationMs()).isEqualTo(task1Updated.getExpirationMs());

    // Verify old time index is gone
    Collection<TimerTask> dueBeforeOld = store.fetchTasksDueBefore(task1.getExpirationMs() + 1);
    assertThat(dueBeforeOld).isEmpty();

    Collection<TimerTask> dueBeforeNew =
        store.fetchTasksDueBefore(task1Updated.getExpirationMs() + 1);
    assertThat(dueBeforeNew).hasSize(1);
  }

  @Test
  void testFetchTasksDueBefore() {
    long now = System.currentTimeMillis();
    TimerTask task1 = new TimerTask("task1", "client1", 1000, null, 0, () -> {}); // due in 1s
    TimerTask task2 = new TimerTask("task2", "client1", 5000, null, 0, () -> {}); // due in 5s
    TimerTask task3 = new TimerTask("task3", "client1", 10000, null, 0, () -> {}); // due in 10s

    store.save(task1);
    store.save(task2);
    store.save(task3);

    Collection<TimerTask> dueTasks = store.fetchTasksDueBefore(now + 6000);
    assertThat(dueTasks).hasSize(2);
    assertThat(dueTasks)
        .extracting(TimerTask::getTaskId)
        .containsExactlyInAnyOrder("task1", "task2");

    // Verify they are NOT removed from store automatically
    assertThat(store.findById("task1")).isPresent();
    assertThat(store.findById("task2")).isPresent();
    assertThat(store.findById("task3")).isPresent();

    // Manually delete and verify
    store.delete(task1);
    assertThat(store.findById("task1")).isEmpty();
  }

  @Test
  void testDelete() {
    TimerTask task = new TimerTask("task1", "client1", 10000, null, 0, () -> {});
    store.save(task);
    assertThat(store.findById("task1")).isPresent();

    store.delete(task);

    // Explicitly check both indexes are cleared (findById checks both)
    assertThat(store.findById("task1")).isEmpty();

    // Check time index directly via fetch
    Collection<TimerTask> due = store.fetchTasksDueBefore(task.getExpirationMs() + 1);
    assertThat(due).isEmpty();
  }

  @Test
  void testIsAfterBoundaryCases() {
    TimerTask task = new TimerTask("task1", "client1", 5000, null, 0, () -> {}); // exp = now + 5000
    store.save(task);

    // Exactly at expiration: should NOT be after
    Collection<TimerTask> dueAt = store.fetchTasksDueBefore(task.getExpirationMs());
    assertThat(dueAt).hasSize(1);

    // 1ms before expiration: should NOT be after
    Collection<TimerTask> dueBefore = store.fetchTasksDueBefore(task.getExpirationMs() - 1);
    assertThat(dueBefore).isEmpty();

    // 1ms after expiration: should be after
    Collection<TimerTask> dueAfter = store.fetchTasksDueBefore(task.getExpirationMs() + 1);
    assertThat(dueAfter).hasSize(1);
  }

  @Test
  void testSerializationWithEmptyPayload() throws IOException {
    TimerTask task = new TimerTask("task1", "client1", 10000, new byte[0], 0, () -> {});
    byte[] data = TimerTaskSerializer.serialize(task);
    TimerTask deserialized = TimerTaskSerializer.deserialize(data);

    assertThat(deserialized.getClientId()).isEqualTo("client1");
    assertThat(deserialized.getPayload()).isEmpty();
  }

  @Test
  void testCreateTimeKeyIntegrity() {
    // Create two tasks with SAME expiration but DIFFERENT IDs
    long expiration = System.currentTimeMillis() + 10000;
    TimerTask task1 = TimerTask.withExpiration("a", "client1", expiration, null, 0, 0, () -> {});
    TimerTask task2 = TimerTask.withExpiration("b", "client1", expiration, null, 0, 0, () -> {});

    store.save(task1);
    store.save(task2);

    assertThat(store.findById("a")).isPresent();
    assertThat(store.findById("b")).isPresent();

    Collection<TimerTask> due = store.fetchTasksDueBefore(expiration + 1);
    assertThat(due).hasSize(2).extracting(TimerTask::getTaskId).containsExactlyInAnyOrder("a", "b");
  }

  @Test
  void testListWithFiltersAndPagination() {
    long now = System.currentTimeMillis();
    // 5 tasks: 3 for client1, 2 for client2. 3 are recurring, 2 are one-shot.
    TimerTask t1 =
        TimerTask.withExpiration("t1", "client1", now + 1000, null, 0, 0, () -> {}); // One-shot
    TimerTask t2 =
        TimerTask.withExpiration("t2", "client1", now + 2000, null, 1000, 0, () -> {}); // Recurring
    TimerTask t3 =
        TimerTask.withExpiration("t3", "client2", now + 3000, null, 1000, 0, () -> {}); // Recurring
    TimerTask t4 =
        TimerTask.withExpiration("t4", "client1", now + 4000, null, 1000, 0, () -> {}); // Recurring
    TimerTask t5 =
        TimerTask.withExpiration("t5", "client2", now + 5000, null, 0, 0, () -> {}); // One-shot

    store.save(t1);
    store.save(t2);
    store.save(t3);
    store.save(t4);
    store.save(t5);

    // 1. List for client1 only
    ListResult<TimerTask> r1 = store.list("client1", 0, Long.MAX_VALUE, null, 10, null);
    assertThat(r1.items())
        .hasSize(3)
        .extracting(TimerTask::getTaskId)
        .containsExactly("t1", "t2", "t4");

    // 2. List recurring tasks only
    ListResult<TimerTask> r2 = store.list(null, 0, Long.MAX_VALUE, true, 10, null);
    assertThat(r2.items())
        .hasSize(3)
        .extracting(TimerTask::getTaskId)
        .containsExactly("t2", "t3", "t4");

    // 3. List with pagination (limit 2)
    ListResult<TimerTask> page1 = store.list(null, 0, Long.MAX_VALUE, null, 2, null);
    assertThat(page1.items())
        .hasSize(2)
        .extracting(TimerTask::getTaskId)
        .containsExactly("t1", "t2");
    assertThat(page1.nextToken()).isNotNull();

    ListResult<TimerTask> page2 = store.list(null, 0, Long.MAX_VALUE, null, 2, page1.nextToken());
    assertThat(page2.items())
        .hasSize(2)
        .extracting(TimerTask::getTaskId)
        .containsExactly("t3", "t4");
    assertThat(page2.nextToken()).isNotNull();

    ListResult<TimerTask> page3 = store.list(null, 0, Long.MAX_VALUE, null, 2, page2.nextToken());
    assertThat(page3.items()).hasSize(1).extracting(TimerTask::getTaskId).containsExactly("t5");
    assertThat(page3.nextToken()).isNull();

    // 4. List within time range
    ListResult<TimerTask> range = store.list(null, now + 1500, now + 4500, null, 10, null);
    assertThat(range.items())
        .hasSize(3)
        .extracting(TimerTask::getTaskId)
        .containsExactly("t2", "t3", "t4");
  }
}
