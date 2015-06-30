package net.f4grx.audiobpsk;

/**
 *
 * @author f4grx
 */
public interface RecorderCallback {
    public void onBuffer(double[] buf);
    public void onLock(boolean locked, double error, double freq);
}
