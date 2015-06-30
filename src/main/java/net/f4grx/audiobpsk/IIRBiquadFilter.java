package net.f4grx.audiobpsk;

/**
 * https://en.wikipedia.org/wiki/Bilinear_transform
 * https://en.wikipedia.org/wiki/Digital_biquad_filter
 * http://www.robots.ox.ac.uk/~sjrob/Teaching/SP/l6.pdf <- IMPORTANT
 * @author f4grx
 */
public class IIRBiquadFilter {
    private final double[] xv;
    private final double[] yv;
    private final double fsample;
    
/*
http://www-users.cs.york.ac.uk/~fisher/cgi-bin/mkfscript
Command line: /www/usr/fisher/helpers/mkfilter -Bu -Lp -o 2 -a 2.2675736961e-01 0.0000000000e+00
raw alpha1    =   0.2267573696
raw alpha2    =   0.2267573696
warped alpha1 =   0.2749160457
warped alpha2 =   0.2749160457
gain at dc    :   mag = 3.978041310e+00   phase =   0.0000000000 pi
gain at centre:   mag = 2.812899986e+00   phase =  -0.5000000000 pi
gain at hf    :   mag = 0.000000000e+00

S-plane zeros:

S-plane poles:
	 -1.2214198088 + j   1.2214198088
	 -1.2214198088 + j  -1.2214198088

Z-plane zeros:
	 -1.0000000000 + j   0.0000000000	2 times

Z-plane poles:
	  0.0856206952 + j   0.4116193172
	  0.0856206952 + j  -0.4116193172

Recurrence relation:
y[n] = (  1 * x[n- 2])
     + (  2 * x[n- 1])
     + (  1 * x[n- 0])

     + ( -0.1767613657 * y[n- 2])
     + (  0.1712413904 * y[n- 1])
*/
    double b0 = 1;
    double b1 = 2;
    double b2 = 1;
    double a0 = 3.978041310e+00;
    double a1 = 0.1712413904;
    double a2 = -0.1767613657;

    public IIRBiquadFilter(int order, double fs) {
        xv = new double[order+1];
        yv = new double[order+1];
        fsample = fs;
    }
    
    //compute filter coefs for low pass at fs freq
    public void setCoefsButterworth(double fc) {
        double wd = 2 * Math.PI * fc;
        
        double wa = (2*fsample) * Math.tan(wd / (2*fsample));
    }
    
    public double process(double in) {
        xv[0] = xv[1];
        xv[1] = xv[2]; 
        xv[2] = in / a0;
        yv[0] = yv[1];
        yv[1] = yv[2]; 
        yv[2] =   
                ( xv[0] * b2 ) + 
                ( xv[1] * b1 ) + 
                ( xv[2] * b0 ) + 
                ( yv[0] * a2) +
                ( yv[1] * a1);
        return yv[2];
    }
}
