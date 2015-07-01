package net.f4grx.audiobpsk;

import java.util.concurrent.LinkedBlockingQueue;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 *
 * @author f4grx
 */
public abstract class BpskDecoder implements Runnable {

    protected static final double TWOPI = 2 * Math.PI;

    private Thread t;
    private boolean running;
    private boolean complete;

    protected final TargetDataLine line;

    protected final int samplerate;
    protected final int bufsize;
    protected final LinkedBlockingQueue<Object> q;
    protected BpskDecoderCallback callback;

    protected AudioFormat format;

    protected abstract boolean init();
    protected abstract boolean process(double[] buf);
    protected abstract boolean done();

    protected BpskDecoder(TargetDataLine l) {
        line = l;
        samplerate = 44100;
        bufsize = 8192;
        q = new LinkedBlockingQueue<>();
    }

    public void setCallback(BpskDecoderCallback cb) {
        callback = cb;
    }

    public void run() {
        //--------------------
        //Audio init
        //--------------------
        
        format = new AudioFormat(samplerate, 16, 2, true, false);
        try {
            line.open(format, bufsize);
            System.out.println("line opened ->" + format);
            line.start();
            System.out.println("line started");
        } catch (LineUnavailableException ex) {
            System.out.println("line unavailable");
            complete = true;
            return;
        }
        
        //--------------------
        //User init
        //--------------------
        complete = !init();
        if(complete) {
            System.out.println("user init failed");
            return;
        }
        
        //--------------------
        //start buffers processing
        //--------------------
        
        running = true;
        int samples = bufsize / format.getFrameSize();
        System.out.println("samples per buffer:" + samples + " duration(ms):" + 1000.0 * (double) samples / (double) samplerate);
        byte[] buf = new byte[bufsize];
        double[] fltbuf = new double[samples];
        int offset;
        int index;
        
        while (running) {
            line.read(buf, 0, bufsize);
            index = 0;
            offset = 0;
            while (offset < bufsize) {
                offset = getsample(format, buf, offset, fltbuf, index);
                index++;
            }
            boolean okay = process(fltbuf);
            if(!okay) {
                System.out.println("problem with buffer");
                running = false;
                break;
            }
            if(t.isInterrupted()) {
                System.out.println("thread interrupted");
                running = false;
                break;
            }
        }
        
        //--------------------
        //User cleanup
        //--------------------
        done();

        //--------------------
        //Audio cleanup
        //--------------------
        line.stop();
        line.flush();
        line.close();
        complete = true;
    }

    public void start() {
        t = new Thread(this);
        t.start();
    }

    public void stop() {
        running = false;
        t.interrupt();
        System.out.println("waiting");
        while (!complete) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException ex) {
            }
        }
        t = null;
        System.out.println("closed");
    }

    public int getSampleRate() {
        return samplerate;
    }

    protected int getsample(AudioFormat af, byte[] buf, int offset, double[] dest, int destoff) {
        int sb = af.getSampleSizeInBits();
        int samp = 0;
        int samp1, samp2;
        double dsamp = 0;

        int chs = af.getChannels();
        for (int ch = 0; ch < chs; ch++) {
            if (sb == 16) {
                samp1 = (int) buf[offset++] & 0xFF;
                samp2 = (int) buf[offset++] & 0xFF;
                if (af.isBigEndian()) {
                    samp = (samp1 << 8) | samp2;
                } else {
                    samp = (samp2 << 8) | samp1;
                }
                if (af.getEncoding() == AudioFormat.Encoding.PCM_SIGNED && ((samp & 0x8000) == 0x8000)) {
                    samp |= 0xFFFF0000;
                }
                dsamp += (double) samp / 32768.0d;
            } else if (sb == 8) {
                samp = (int) buf[offset++] & 0xFF;
                if (af.getEncoding() == AudioFormat.Encoding.PCM_SIGNED && ((samp & 0x80) == 0x80)) {
                    samp |= 0xFFFFFF00;
                }
                dsamp += (double) samp / 128.0d;
            } else {
                offset++;
            }
        }
        dest[destoff] = dsamp / (double) chs;

        return offset;
    }
    
    /* digital part */
    public void setBaud(int br) {
        System.out.println("baudrate -> " + br);
    }

}
