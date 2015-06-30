/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.f4grx.audiobpsk;

import javax.sound.sampled.Mixer;

/**
 *
 * @author slo
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
