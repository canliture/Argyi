# Argyi
A tool for automatic C integer error fixing

Introduction
------------
Argyi is a tool for automatic C integer error fixing. An integer error in C program can be overflow/underflow error, unexpected signedness conversion, unexpected lossy truncation and undefined behaviors involving integer operation.
In general, they are covered by CWE190, 191, 194, 195, 196, 197 and 680. The correctness of integer operation is usually under-specified in practice. 
Therefore, we can effectively locate and fix integer errors by inference of possible values for program expressions. Based on this insight, we design a framework
Range Analysis Based Integer Error Fixing (RABIEF for short) framework to automate C integer error fixing.

Argyi is also the prototype tool for the paper "RABIEF: A Framework for Automatic C Integer Error Fixing"

Why it is called "Argyi"
------------------------
This name is inspired by the name of a plant *Artemisia argyi*, the Chinese mugwort, which is s strongly aromatic and has medical effects.

Requirements
------------
OS: Linux distributions (Ubuntu 14.04 LTS is recommended)

[GCC](https://gcc.gnu.org/): the GNU Compiler Collection (our experiments are based on v5.2.1)

[CIL](https://www.cs.berkeley.edu/~necula/cil/): Infrastructure for C Program Analysis and Transformation (v1.3.7)

[Z3](https://github.com/Z3Prover/z3): Z3 Theorem Prover (v4.4.2)
