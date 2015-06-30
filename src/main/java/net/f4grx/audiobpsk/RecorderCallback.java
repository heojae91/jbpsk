/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.f4grx.audiobpsk;

/**
 *
 * @author slo
 */
public interface RecorderCallback {
    public void onBuffer(double[] buf);
}
