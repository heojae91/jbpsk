package net.f4grx.audiobpsk;

import javax.sound.sampled.Mixer;

/**
 *
 * @author f4grx
 */
class MixerProxy {
    private final Mixer mix;

    public MixerProxy(Mixer m) {
        mix = m;
    }

    public String toString() {
        return mix.getMixerInfo().getName();
    }

    public Mixer getMixer() {
        return mix;
    }
    
}
