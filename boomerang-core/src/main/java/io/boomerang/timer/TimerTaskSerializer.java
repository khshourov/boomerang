package io.boomerang.timer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Utility for serializing and deserializing {@link TimerTask} objects for persistent storage.
 *
 * <p>This serializer only handles fields that are persistent. The {@link Runnable} component is not
 * serialized and is replaced by a placeholder upon deserialization.
 *
 * @since 1.0.0
 */
public class TimerTaskSerializer {
  private TimerTaskSerializer() {}

  /**
   * Serializes a {@link TimerTask} into a byte array.
   *
   * @param task the task to serialize; must be non-null
   * @return the serialized byte array
   * @throws IOException if an error occurs during serialization
   */
  public static byte[] serialize(TimerTask task) throws IOException {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos)) {
      dos.writeUTF(task.getTaskId());
      dos.writeLong(task.getExpirationMs());
      dos.writeLong(task.getRepeatIntervalMs());

      byte[] payload = task.getPayload();
      if (payload == null) {
        dos.writeInt(-1);
      } else {
        dos.writeInt(payload.length);
        dos.write(payload);
      }
      return baos.toByteArray();
    }
  }

  /**
   * Deserializes a byte array into a {@link TimerTask}.
   *
   * @param data the serialized task data; must be non-null
   * @return the deserialized {@link TimerTask}
   * @throws IOException if an error occurs during deserialization
   */
  public static TimerTask deserialize(byte[] data) throws IOException {
    try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
        DataInputStream dis = new DataInputStream(bais)) {
      String taskId = dis.readUTF();
      long expirationMs = dis.readLong();
      long repeatIntervalMs = dis.readLong();

      int payloadLength = dis.readInt();
      byte[] payload = null;
      if (payloadLength >= 0) {
        payload = new byte[payloadLength];
        dis.readFully(payload);
      }

      // IMPORTANT: Using a placeholder Runnable as the task itself is not serializable.
      // The dispatcher in TieredTimer handles actual execution.
      return TimerTask.withExpiration(taskId, expirationMs, payload, repeatIntervalMs, () -> {});
    }
  }
}
