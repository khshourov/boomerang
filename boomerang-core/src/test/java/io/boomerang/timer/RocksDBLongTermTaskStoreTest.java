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
    TimerTask task = new TimerTask("task1", 10000, "payload".getBytes(), 0, () -> {});
    store.save(task);

    Optional<TimerTask> found = store.findById("task1");
    assertThat(found).isPresent();
    assertThat(found.get().getTaskId()).isEqualTo("task1");
    assertThat(found.get().getExpirationMs()).isEqualTo(task.getExpirationMs());
    assertThat(found.get().getPayload()).isEqualTo("payload".getBytes());
  }

  @Test
  void testUpdateTask() {
    TimerTask task1 = new TimerTask("task1", 10000, "payload1".getBytes(), 0, () -> {});
    store.save(task1);

    TimerTask task1Updated = new TimerTask("task1", 20000, "payload2".getBytes(), 0, () -> {});
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
    TimerTask task1 = new TimerTask("task1", 1000, null, 0, () -> {}); // due in 1s
    TimerTask task2 = new TimerTask("task2", 5000, null, 0, () -> {}); // due in 5s
    TimerTask task3 = new TimerTask("task3", 10000, null, 0, () -> {}); // due in 10s

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
    TimerTask task = new TimerTask("task1", 10000, null, 0, () -> {});
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
    TimerTask task = new TimerTask("task1", 5000, null, 0, () -> {}); // exp = now + 5000
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
    TimerTask task = new TimerTask("task1", 10000, new byte[0], 0, () -> {});
    byte[] data = TimerTaskSerializer.serialize(task);
    TimerTask deserialized = TimerTaskSerializer.deserialize(data);

    assertThat(deserialized.getPayload()).isEmpty();
  }

  @Test
  void testCreateTimeKeyIntegrity() {
    // Create two tasks with SAME expiration but DIFFERENT IDs
    long expiration = System.currentTimeMillis() + 10000;
    TimerTask task1 = TimerTask.withExpiration("a", expiration, null, 0, () -> {});
    TimerTask task2 = TimerTask.withExpiration("b", expiration, null, 0, () -> {});

    store.save(task1);
    store.save(task2);

    assertThat(store.findById("a")).isPresent();
    assertThat(store.findById("b")).isPresent();

    Collection<TimerTask> due = store.fetchTasksDueBefore(expiration + 1);
    assertThat(due).hasSize(2).extracting(TimerTask::getTaskId).containsExactlyInAnyOrder("a", "b");
  }
}
