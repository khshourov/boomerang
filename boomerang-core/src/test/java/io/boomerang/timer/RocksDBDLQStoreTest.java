package io.boomerang.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.boomerang.config.ServerConfig;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocksDBDLQStoreTest {
  @TempDir Path tempDir;

  private RocksDBDLQStore store;
  private ServerConfig serverConfig;

  @BeforeEach
  void setUp() {
    serverConfig = mock(ServerConfig.class);
    when(serverConfig.getRocksDbDlqPath()).thenReturn(tempDir.resolve("dlq").toString());
    store = new RocksDBDLQStore(serverConfig);
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
    DLQStore.DLQEntry entry = new DLQStore.DLQEntry(task, "failure reason");
    store.save(entry);

    Optional<DLQStore.DLQEntry> found = store.findEntryById("client1", "task1");
    assertThat(found).isPresent();
    assertThat(found.get().task().getTaskId()).isEqualTo("task1");
    assertThat(found.get().task().getClientId()).isEqualTo("client1");
    assertThat(found.get().errorMessage()).isEqualTo("failure reason");
  }

  @Test
  void testFindAll() {
    TimerTask task1 = new TimerTask("task1", "client1", 1000, null, 0, () -> {});
    TimerTask task2 = new TimerTask("task2", "client2", 2000, null, 0, () -> {});
    store.save(new DLQStore.DLQEntry(task1, "error1"));
    store.save(new DLQStore.DLQEntry(task2, "error2"));

    Collection<DLQStore.DLQEntry> all = store.findAll();
    assertThat(all)
        .hasSize(2)
        .extracting(e -> e.task().getTaskId())
        .containsExactlyInAnyOrder("task1", "task2");
  }

  @Test
  void testFindAllByClientId() {
    TimerTask task1 = new TimerTask("task1", "client1", 1000, null, 0, () -> {});
    TimerTask task2 = new TimerTask("task2", "client2", 2000, null, 0, () -> {});
    TimerTask task3 = new TimerTask("task3", "client1", 3000, null, 0, () -> {});
    store.save(new DLQStore.DLQEntry(task1, "error1"));
    store.save(new DLQStore.DLQEntry(task2, "error2"));
    store.save(new DLQStore.DLQEntry(task3, "error3"));

    Collection<DLQStore.DLQEntry> client1Entries = store.findAll("client1");
    assertThat(client1Entries)
        .hasSize(2)
        .extracting(e -> e.task().getTaskId())
        .containsExactlyInAnyOrder("task1", "task3");

    Collection<DLQStore.DLQEntry> client2Entries = store.findAll("client2");
    assertThat(client2Entries)
        .hasSize(1)
        .extracting(e -> e.task().getTaskId())
        .containsExactly("task2");

    Collection<DLQStore.DLQEntry> unknownEntries = store.findAll("unknown");
    assertThat(unknownEntries).isEmpty();
  }

  @Test
  void testDelete() {
    TimerTask task = new TimerTask("task1", "client1", 1000, null, 0, () -> {});
    store.save(new DLQStore.DLQEntry(task, "error"));
    assertThat(store.findEntryById("client1", "task1")).isPresent();

    store.delete("client1", "task1");

    assertThat(store.findEntryById("client1", "task1")).isEmpty();
  }

  @Test
  void testClear() {
    store.save(
        new DLQStore.DLQEntry(
            new TimerTask("task1", "client1", 1000, null, 0, () -> {}), "error1"));
    store.save(
        new DLQStore.DLQEntry(
            new TimerTask("task2", "client1", 2000, null, 0, () -> {}), "error2"));
    assertThat(store.findAll()).hasSize(2);

    store.clear();

    assertThat(store.findAll()).isEmpty();
  }
}
