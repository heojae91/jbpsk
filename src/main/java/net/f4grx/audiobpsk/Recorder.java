package net.f4grx.audiobpsk;

import java.util.concurrent.LinkedBlockingQueue;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 *
 * @author f4grx
 */
public class Recorder implements Runnable {

    private final TargetDataLine line;
    private final int samplerate = 44100;
    private final int bufsize = 8192;
    private double tone;
    private boolean running;
    private boolean complete;
    private Thread t;
    private RecorderCallback callback;
    private final IIRBiquadFilter fi = new IIRBiquadFilter(2, samplerate);
    private final IIRBiquadFilter fq = new IIRBiquadFilter(2, samplerate);
    
    LinkedBlockingQueue<Byte> q;

    Recorder(TargetDataLine l) {
        line = l;
        tone = 1000;
        q = new LinkedBlockingQueue<>();
    }

    public void setCallback(RecorderCallback cb) {
        callback = cb;
    }
    
    private static final int NZEROS = 2;
    private static final int NPOLES = 2;
    
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
        double omega;
        double phase = 0;
        int samples = bufsize / af.getFrameSize();
        System.out.println("samples per buffer:" + samples + " duration(ms):" + 1000.0 * (double) samples / (double) samplerate);
        int offset;
        byte[] buf = new byte[bufsize];
        double[] fltbuf = new double[samples];
        double si, sq, sample, sim, sqm;
        double xv[] = new double[NZEROS+1];
        double yv[] = new double[NPOLES+1];
        double error;
        
        //low pass filter coefficients


        while (running) {
            try {
                line.read(buf, 0, bufsize);
                offset = 0;
                int index = 0;
                double erroravg = 0;
                
                //For each sample
                while(offset<bufsize) {
                    offset = getsample(af,buf,offset,fltbuf,index);
                    sample = fltbuf[index];
                    index++;
                    
                    //Update VCO
                    omega = 2 * Math.PI * tone / (double) samplerate;
                    phase += omega;
                    si = Math.cos(phase);
                    sq = Math.sin(phase);

                    //Costas loop
                    //Mix
                    sim = si * sample;
                    sqm = sq * sample;

                    //LPF 
                    sim = fi.process(sim);
                    sqm = fq.process(sqm);
                    
                    //Multiply to get error term
                    error = sim * sqm;
                    
                    //should also be LPFed
                    erroravg += error;
                    
                    //Loop gain...
                    
                    //update VCO
                    tone -= error;
                    
                    //System.out.println( (""+sample+"\t"+error).replace('.', ',') );
                }
                
                if(callback!=null) {
                    callback.onBuffer(fltbuf);
                    callback.onLock(false, erroravg, tone);
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
                samp1 = (int)buf[offset++] & 0xFF;
                samp2 = (int)buf[offset++] & 0xFF;
                if (af.isBigEndian()) {
                    samp = (samp1 << 8) | samp2;
                } else {
                    samp = (samp2 << 8) | samp1;
                }
                if(af.getEncoding()==AudioFormat.Encoding.PCM_SIGNED && ((samp&0x8000)==0x8000) ) {
                    samp |= 0xFFFF0000;
                }
                dsamp += (double)samp / 32768.0d;
            } else if (sb == 8) {
                samp = (int)buf[offset++] & 0xFF;
                if(af.getEncoding()==AudioFormat.Encoding.PCM_SIGNED && ((samp&0x80)==0x80) ) {
                    samp |= 0xFFFFFF00;
                }
                dsamp += (double)samp / 128.0d;
            } else {
                offset++;
            }
        }
        dest[destoff] = dsamp / (double)chs;

        return offset;
    }

    public void setBaud(int br) {
        System.out.println("baudrate -> "+br);
    }
    
    public void setLocalOsc(int t) {
        tone = t;
        System.out.println("LO freq -> "+t);
    }

}
