package net.f4grx.audiobpsk;

import biz.source_code.dsp.filter.FilterCharacteristicsType;
import biz.source_code.dsp.filter.FilterPassType;
import biz.source_code.dsp.filter.IirFilter;
import biz.source_code.dsp.filter.IirFilterCoefficients;
import biz.source_code.dsp.filter.IirFilterDesignFisher;
import java.util.concurrent.LinkedBlockingQueue;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 *
 * @author f4grx
 */
public class Recorder implements Runnable {

    private Thread t;
    private boolean running;
    private boolean complete;
    private final TargetDataLine line;
    private final int bufsize;
    private final int samplerate;
    LinkedBlockingQueue<Byte> q;
    private RecorderCallback callback;

    
    private double freq;
    private IirFilter fi, fq;
    private double omega;

    Recorder(TargetDataLine l) {
        line = l;
        samplerate = 44100;
        bufsize = 8192;   
        freq = 1000;
        q = new LinkedBlockingQueue<>();
        
        IirFilterCoefficients coeffs1 = IirFilterDesignFisher.design(
                FilterPassType.lowpass, 
                FilterCharacteristicsType.butterworth, 
                1, //filterOrder, 
                0, //filterRipple, 
                0.2, //10000.0/(double)samplerate, //fcf1Rel, 
                0 //fcf2Rel
                );
        fi = new IirFilter(coeffs1);
        fq = new IirFilter(coeffs1);
    }

    public void setCallback(RecorderCallback cb) {
        callback = cb;
    }

    private static final double TWOPI = 2*Math.PI;
    
    @Override
    public void run() {
        System.out.println("recorder start");
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

        //start generation
        running = true;
        complete = false;
        double phase = 0;
        int samples = bufsize / af.getFrameSize();
        System.out.println("samples per buffer:" + samples + " duration(ms):" + 1000.0 * (double) samples / (double) samplerate);
        int offset;
        byte[] buf = new byte[bufsize];
        double[] fltbuf = new double[samples];
        double si, sq, sample, sim, sqm;
        double error = 0;
        omega = TWOPI * freq / (double) samplerate;

        //low pass filter coefficients
        while (running) {
            double alpha = 0.1;
            double beta = (alpha * alpha) / 4.0d;
            double errtot;
            try {
                line.read(buf, 0, bufsize);
                offset = 0;
                int index = 0;
                errtot = 0;

                //For each sample
                while (offset < bufsize) {
                    offset = getsample(af, buf, offset, fltbuf, index);
                    sample = fltbuf[index];
                    index++;

                    //Update VCO
                    phase += omega;
                    phase += alpha * error;

                    omega += beta  * error;

                    //bind freq
                    freq = (omega*samplerate)/TWOPI;
                    if(freq < 100) {
                        omega = (TWOPI*500/samplerate);
                    }
                    if(freq > samplerate/2) {
                        omega = (TWOPI*4000/samplerate);
                    }

                    if(phase>TWOPI) {
                        phase -= TWOPI;
                    }
                    si = Math.cos(phase);
                    sq = -Math.sin(phase);

                    //Costas loop
                    //Mix step
                    sim = si * sample;
                    sqm = sq * sample;

                    //LPF step
                    sim = fi.step(sim);
                    sqm = fq.step(sqm);

                    //Multiply to get error term
                    error = sim * sqm;

                    errtot += error;
                    
                }
                
                if (callback != null) {
                    callback.onBuffer(fltbuf, freq);
                    boolean locked = errtot<1e-4;
                    callback.onLock(locked, errtot, freq);
                    if(!locked) {
                        //System.out.println("unlock err "+errtot);
                    }
                }

                if (Thread.interrupted()) {
                    throw new InterruptedException("interrupt pending");
                }
            } catch (InterruptedException ex) {
                System.out.println("interrupted:" + ex.getMessage());
                running = false;
            }
        }
        System.out.println("recorder end");
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

    private int getsample(AudioFormat af, byte[] buf, int offset, double[] dest, int destoff) {
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

    public void setBaud(int br) {
        System.out.println("baudrate -> " + br);
    }

    public void setLocalOsc(int t) {
        freq = t;
        System.out.println("LO freq -> " + t);
    }

    public int getSampleRate() {
        return samplerate;
    }

    void hintFreq(double hfreq) {
        omega = TWOPI * hfreq / (double) samplerate;
    }

}
