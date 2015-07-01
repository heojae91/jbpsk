package net.f4grx.audiobpsk;

import biz.source_code.dsp.filter.FilterCharacteristicsType;
import biz.source_code.dsp.filter.FilterPassType;
import biz.source_code.dsp.filter.IirFilter;
import biz.source_code.dsp.filter.IirFilterCoefficients;
import biz.source_code.dsp.filter.IirFilterDesignFisher;
import javax.sound.sampled.TargetDataLine;

/**
 *
 * @author f4grx
 */
public class BpskCostasLoopDecoder extends BpskDecoder {

    private final IirFilter fi;
    private final IirFilter fq;
    private double omega;
    private double error;
    private double phase;

    public BpskCostasLoopDecoder(TargetDataLine l) {
        super(l);
        IirFilterCoefficients coeffs = IirFilterDesignFisher.design(
                FilterPassType.lowpass,
                FilterCharacteristicsType.butterworth,
                1, //filterOrder, 
                0, //filterRipple, 
                0.1, //fcf1Rel, 
                0 //fcf2Rel
        );
        fi = new IirFilter(coeffs);
        fq = new IirFilter(coeffs);
    }

    public synchronized void hintFreq(double hfreq) {
        omega = TWOPI * hfreq / (double) samplerate;
        System.out.println("set freq "+hfreq);
    }

    @Override
    protected boolean init() {
        System.out.println("costas recorder start");
        //start generation
        phase = 0;
        omega = TWOPI * 1000.0 / (double) samplerate;
        error = 0;
        return true;
    }

    @Override
    public boolean process(double[] buf) {
        int ns = buf.length;
        double alpha = 0.2; //feedback coef
        double beta = (alpha * alpha) / 4.0d;
        double errtot = 0;
        //For each sample
        for (int index = 0; index < ns; index++) {
            double si, sq, sample, sim, sqm;
            sample = buf[index];

            //Update VCO
            phase += omega;
            phase += alpha * error;

            omega += beta * error;

            //bind freq
            double freq = (omega * samplerate) / TWOPI;
            if (freq < 100) {
                freq = 100;
                omega = (TWOPI * freq) / samplerate;
                System.out.println("freq low");
            }
            if (freq > samplerate / 2) {
                freq = (samplerate / 2);
                omega = (TWOPI * freq) / samplerate;
                System.out.println("freq hi");
            }

            if (phase > TWOPI) {
                phase -= TWOPI;
            }
            si = Math.cos(phase);
            sq = -Math.sin(phase); //minus sign important

            //Costas loop Mix step
            sim = si * sample;
            sqm = sq * sample;

            //Costas loop LPF step
            sim = fi.step(sim);
            sqm = fq.step(sqm);

            //Costas loop Multiply to get error term
            error = sim * sqm;

            //Accumulate error over a buffer to indicate lock?
            errtot += error;
        }
        
        if (callback != null) {
            double freq = (omega * samplerate) / TWOPI;
            callback.onBuffer(buf, freq);
            boolean locked = errtot < 1e-4;
            callback.onLock(locked, errtot, freq);
            //if (!locked) {
                //System.out.println("unlock err "+errtot);
            //}
        }
        return true;
    }

    @Override
    protected boolean done() {
        System.out.println("costas recorder end");
        return true;
    }
}
