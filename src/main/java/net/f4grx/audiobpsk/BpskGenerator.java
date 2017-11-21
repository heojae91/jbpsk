package net.f4grx.audiobpsk;

import java.util.concurrent.LinkedBlockingQueue;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import biz.source_code.dsp.filter.IirFilter;
import biz.source_code.dsp.filter.FilterCharacteristicsType;
import biz.source_code.dsp.filter.FilterPassType;
import biz.source_code.dsp.filter.IirFilterCoefficients;
import biz.source_code.dsp.filter.IirFilterDesignFisher;

/**
 *
 * @author f4grx
 */
class BpskGenerator implements Runnable {
    private final SourceDataLine line;
    private final int samplerate;
    private double freq;
    private boolean running;
    private boolean complete;
    private Thread t;
    private int baudrate;
    private int baudcount; //number of samples per baud
    private int currentbyte;
    private int sendbits; //number of bits to send from current byte
    
    LinkedBlockingQueue<Byte> q;
    private final int bufsize;

    private final IirFilter bpFilter;
    
    public BpskGenerator(SourceDataLine l) {
        line = l;
        samplerate = 44100;
        bufsize = 8192;
        baudrate = 0;
        baudcount = 0;
        sendbits = 0;
        q = new LinkedBlockingQueue<>();
        IirFilterCoefficients coeffs = IirFilterDesignFisher.design(
                FilterPassType.lowpass,
                FilterCharacteristicsType.butterworth,
                1, //filterOrder, 
                0, //filterRipple, 
                18000, //fcf1Rel, 
                22000 //fcf2Rel
        );
        bpFilter = new IirFilter(coeffs);
    }

    private static final double TWOPI = 2 * Math.PI;
    
    public void run() {
        System.out.println("player start");
        AudioFormat af = new AudioFormat(samplerate, 16, 2, true, false);
        try {
            line.open(af, bufsize);
            System.out.println("line opened ->" + af);
            line.start();
            System.out.println("line started");
        } catch (LineUnavailableException ex) {
            System.out.println("line unavailable");
            complete = true;
            return;
        }
        //baud rate stuff
        if(baudrate>0) {
            baudcount = (int)(samplerate / (double) baudrate);
        }
        //start generation
        running = true;
        complete = false;
        double omega;
        double phase = 0;
        double sign = 1;
        int baudindex = 0;
        int samples = bufsize / af.getFrameSize();
        System.out.println("samples per buffer:" + samples + " duration(ms):" + 1000.0 * (double) samples / (double) samplerate);
        int offset = 0;
        byte[] buf = new byte[bufsize];
        while (running) {
            try {
                offset = 0;
                omega = TWOPI * (double) freq / (double) samplerate;
                for (int i = 0; i < samples; i++) {
                    double sample = Math.sin(phase);
                    phase += omega;
                    if(phase>TWOPI) {
                        phase -= TWOPI;
                    }
                    baudindex += 1;
                    if(baudindex == baudcount) {
                        baudindex = 0;
                        //this is where we reverse the phase if a bit has to be sent
                        if(sendbits>0) {
                            int bit = (currentbyte & 1);
                            if(bit==0) {
                                //phase reversal!
                                sign = -sign;
                            }
                            //System.out.println("->"+bit );
                            currentbyte >>= 1;
                            sendbits -= 1;
                        } else if(!q.isEmpty()) {
                            currentbyte = q.remove();
                            //System.out.println("::"+currentbyte);
                            sendbits = 8;
                        }
                    }
                    sample *= sign;
                    offset = setsample(af, buf, offset, sample);
                    if (offset < 0) {
                        throw new InterruptedException("bad sample format");
                    }
                }
                line.write(buf, 0, bufsize);
                if (Thread.interrupted()) {
                    throw new InterruptedException("interrupt pending");
                }
            } catch (InterruptedException ex) {
                System.out.println("interrupted:" + ex.getMessage());
                running = false;
            }
        }
        System.out.println("player end");
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

    private int setsample(AudioFormat af, byte[] buf, int offset, double sample) {
        int sb = af.getSampleSizeInBits();
        int samp;
        if (sb == 16) {
            samp = (int) (sample * 32768);
        } else if (sb == 8) {
            samp = (int) (sample * 127);
        } else {
            return -1;
        }
        int chs = af.getChannels();
        for (int ch = 0; ch < chs; ch++) {
            if (sb == 16) {
                if (af.isBigEndian()) {
                    buf[offset++] = (byte) (samp >> 8);
                    buf[offset++] = (byte) (samp & 0xff);
                } else {
                    buf[offset++] = (byte) (samp & 0xff);
                    buf[offset++] = (byte) (samp >> 8);
                }
            } else if (sb == 8) {
                buf[offset++] = (byte) (samp & 0xff);
            }
        }
        return offset;
    }

    public void setBaud(int br) {
        baudrate = br;
        System.out.println("baudrate -> "+br);
        if(baudrate>0) {
            baudcount = (int)(samplerate / (double) baudrate);
        } else {
            baudcount = 0;
        }
    }
    
    //Executed in the calling thread
    public void append(String text) {
        byte[] bs = text.getBytes();
        int count = 0;
        for(byte b : bs) {
            q.add(b);
            count++;
        }
        System.out.println("added bytes:"+count);
    }

    public void setFreq(double f) {
        freq = f;
    }
    
}
