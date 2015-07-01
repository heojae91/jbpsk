/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.f4grx.audiobpsk;

import javax.sound.sampled.TargetDataLine;

/**
 *
 * @author slo
 */
public class BpskSquaredDecoder extends BpskDecoder {
    
    public BpskSquaredDecoder(TargetDataLine l) {
        super(l);
    }
    
    protected boolean init() {
        System.out.println("Squared decoder start");
        return true;
    }
    
    @Override
    protected boolean process(double[] buf) {
        return true;
    }

    protected boolean done() {
        System.out.println("Squared decoder done");
        return true;
    }

}
