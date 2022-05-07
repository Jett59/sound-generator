package app.cleancode;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.AudioFileFormat.Type;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

public class Entrypoint {
  private static final int UPSAMPLE_AMOUNT = 147;
  private static final int DOWNSAMPLE_AMOUNT = 160;
  private static final int INITIAL_SAMPLE_RATE = 48000;
  private static final int FINAL_SAMPLE_RATE =
      INITIAL_SAMPLE_RATE * UPSAMPLE_AMOUNT / DOWNSAMPLE_AMOUNT;

  private static final double INPUT1_VOLUME = 1;
  private static final double INPUT2_VOLUME = 1;

  public static void main(String[] args) throws Exception {
    AudioFormat format = new AudioFormat(INITIAL_SAMPLE_RATE, 16, 1, true, false);
    DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
    AudioInputStream input1 =
        AudioSystem.getAudioInputStream(format, AudioSystem.getAudioInputStream(new File("a.wav")));
    AudioInputStream input2 =
        AudioSystem.getAudioInputStream(format, AudioSystem.getAudioInputStream(new File("b.wav")));
    format = new AudioFormat(FINAL_SAMPLE_RATE, 16, 1, true, false);
    SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
    System.out.println("Reading input files");
    byte[] input1Bytes = input1.readAllBytes();
    byte[] input2Bytes = input2.readAllBytes();
    input1.close();
    input2.close();
    System.out.println("Converting to samples");
    short[] input1Samples = toShorts(input1Bytes, ByteOrder.LITTLE_ENDIAN);
    short[] input2Samples = toShorts(input2Bytes, ByteOrder.LITTLE_ENDIAN);
    System.out.println("Upsampling");
    input1Samples = upsample(input1Samples, UPSAMPLE_AMOUNT);
    input2Samples = upsample(input2Samples, UPSAMPLE_AMOUNT);
    System.out.println("Downsampling");
    input1Samples = downsample(input1Samples, DOWNSAMPLE_AMOUNT);
    input2Samples = downsample(input2Samples, DOWNSAMPLE_AMOUNT);
    System.out.println("Adjusting volume");
    input1Samples = adjustVolume(input1Samples, INPUT1_VOLUME);
    input2Samples = adjustVolume(input2Samples, INPUT2_VOLUME);
    int bufferSize = Math.max(input1Samples.length * 2, input2Samples.length * 2);
    ByteBuffer dataBuffer = ByteBuffer.allocate(bufferSize);
    dataBuffer.order(ByteOrder.LITTLE_ENDIAN);
    System.out.println("Writing to buffer");
    for (int i = 0; i < bufferSize / 2; i++) {
      short input1Sample = input1Samples[i % input1Samples.length];
      short input2Sample = input2Samples[i % input2Samples.length];
      short sample = (short) ((input1Sample + input2Sample) / 2);
      dataBuffer.putShort(sample);
    }
    dataBuffer.rewind();
    byte[] data = dataBuffer.array();
    System.out.println("Writing to out.wav");
    ByteArrayInputStream byteInputStream = new ByteArrayInputStream(data);
    AudioInputStream audioInputStream = new AudioInputStream(byteInputStream, format, data.length);
    AudioSystem.write(audioInputStream, Type.WAVE, new File("out.wav"));
    audioInputStream.close();
    System.out.println("Playing");
    line.open(format);
    line.start();
    line.write(data, 0, data.length);
    line.stop();
    line.drain();
    line.close();
    System.out.println("Done");
  }

  private static short[] toShorts(byte[] bytes, ByteOrder byteOrder) {
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    buffer.order(byteOrder);
    short[] result = new short[bytes.length / 2];
    for (int i = 0; i < result.length; i++) {
      result[i] = buffer.getShort();
    }
    return result;
  }

  private static short[] upsample(short[] samples, int factor) {
    short[] result = new short[samples.length * factor];
    for (int i = 0; i < samples.length; i++) {
      short nextSample = (i + 1) < samples.length ? samples[i + 1] : 0;
      short[] upsampledSamples = upsample(samples[i], nextSample, factor);
      System.arraycopy(upsampledSamples, 0, result, i * factor, factor);
    }
    return result;
  }

  private static short[] upsample(short sample, short nextSample, int factor) {
    short[] result = new short[factor];
    for (int i = 0; i < factor; i++) {
      result[i] = sample;
    }
    return result;
  }

  private static short[] downsample(short[] samples, int factor) {
    short[] result = new short[samples.length / factor];
    for (int i = 0; i < result.length; i++) {
      result[i] = samples[i * factor];
    }
    return result;
  }

  private static short[] adjustVolume(short[] samples, double volume) {
    short[] result = new short[samples.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = (short) (samples[i] * volume);
    }
    return result;
  }
}
