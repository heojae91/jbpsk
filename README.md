# jbpsk
bpsk encoder/decoder using java audio

What is this
============

This project is a kind of demo that shows how to naively generate a BPSK signal and how to receive it.

You need a reasonably fast computer to handle the FFT to display the spectrum on the rx side. This is pure java!
The demodulator is also not optimized.

What it does
============

BPSK stands for binary phase shift keying. This means transmitting digital data on a audio (or radio) signal by modulating the phase of the wave.
Data is sent as 8-bits packets at your desired baud rate. When a 1 is sent, the wave is not touched, but when a zero is sent, the wave phase is inverted (or shifted 180 degrees).
So this is really DBPSK, because the encoding is differential. This kind of encoding  is preferred since you have no way to tell that a signal has an absolute phase without a reference signal (we don't want to send this reference as a side channel, this would be higly redundant). So instead we encode data in phase changes, which we can detect for sure by comparing the new signal to what we had before.

Decoding BPSK can be achieved using a number of methods.

One method relies on multiplying the signal by a time shifted version of itself. It only works if you know the baud rate, since the data must be shifted by one bit time.
One other method is to feed the input signal to a PLL; the PLL's VCO will then track the input signal. This is a kind of carrer recovery. Actually this process is a bit more complex, we use a costas loop, that can keep the sync despite the phase changes. This allows us to get a reference signal, and notice the phase changes reliably.

How to use
==========

To use it, you are supposed to run two instances of the program, and connect them via an audio cable.

One instance generates a BPSK signal in the audio range, the other instance shows a spectrum of the incoming audio and receives the data.

Transmitter side
----------------

- select the audio output
- lower the audio volume, or beware, high-level audio tones will hurt your ears, your wife, your newborn, and your neighbours.
- click open, this starts a carrier audio output
- type some data in the text control then press enter, the data is modulated on the carrier. The audio phase is reversed each time a zero bit is sent.
- click close to stop the carrier

Receiver side
-------------
- select the audio input
- open it using the button
- look at the audio spectrum (vertical scale is automatic)

Build
=====
The project uses Maven. It can be built directly on the command line, but also in almost any IDE. I am using Netbeans 8.

Dependencies
============

The project uses the FFT routines from JTransform.

TODO
====
Decoding! For the moment, the only thing done in the receiver is displaying the spectrum. But you will only know that if you RTFM until here.
