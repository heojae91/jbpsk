/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.f4grx.audiobpsk;

import java.util.concurrent.LinkedBlockingQueue;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

/**
 *
 * @author slo
 */
public class Recorder implements Runnable {

    private final TargetDataLine line;
    private final int tone;
    private final int samplerate;
    private boolean running;
    private boolean complete;
    private Thread t;
    private RecorderCallback callback;
    private int baudrate;
    
    LinkedBlockingQueue<Byte> q;
    private double[] fltbuf;

    Recorder(TargetDataLine l) {
        line = l;
        tone = 1000;
        samplerate = 44100;
        q = new LinkedBlockingQueue<>();
    }

    public void setCallback(RecorderCallback cb) {
        callback = cb;
    }
    
    public void run() {
        System.out.println("recorder start");
        AudioFormat af = new AudioFormat(samplerate, 16, 2, true, false);
        int bufsize = 8192;
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
        double omega = 2 * Math.PI * (double) tone / (double) samplerate;
        double phase = 0;
        int samples = bufsize / af.getFrameSize();
        System.out.println("samples per buffer:" + samples + " duration(ms):" + 1000.0 * (double) samples / (double) samplerate);
        int offset;
        byte[] buf = new byte[bufsize];
        fltbuf = new double[samples];
        double[] locbuf = fltbuf;
        
        while (running) {
            try {
                line.read(buf, 0, bufsize);
                offset = 0;
                int index = 0;
                while(offset<bufsize) {
                    offset = getsample(af,buf,offset,locbuf,index++);                    
                }
                if(callback!=null) {
                    callback.onBuffer(locbuf);
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
        baudrate = br;
        System.out.println("baudrate -> "+br);
    }
    
    public void setLocalOsc(int br) {
        baudrate = br;
        System.out.println("LO freq -> "+br);
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
    
    
}
