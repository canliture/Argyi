#ifndef __INTFIX_ARGYI_H_
#define __INTFIX_ARGYI_H_

extern void __INTCHECK_ERROR(const char *);

extern int __INTCHECK_INT_S(long long signed int x);

extern int __INTCHECK_INT_U(long long unsigned int x);

extern unsigned int __INTCHECK_UINT_S(long long signed int x);

extern unsigned int __INTCHECK_UINT_U(long long unsigned int x);

extern short __INTCHECK_SHORT_S(long long signed int x);

extern short __INTCHECK_SHORT_U(long long unsigned int x);

extern unsigned short __INTCHECK_USHORT_S(long long signed int x);

extern unsigned short __INTCHECK_USHORT_U(long long unsigned int x);

extern signed char __INTCHECK_CHAR_S(long long signed int x);

extern signed char __INTCHECK_CHAR_U(long long unsigned int x);

extern unsigned char __INTCHECK_UCHAR_S(long long signed int x);

extern unsigned char __INTCHECK_UCHAR_U(long long unsigned int x);

extern long int __INTCHECK_LINT_S(long long signed int x);

extern long int __INTCHECK_LINT_U(long long unsigned int x);

extern long unsigned int __INTCHECK_ULINT_S(long long signed int x);

extern long unsigned int __INTCHECK_ULINT_U(long long unsigned int x);

extern long long int __INTCHECK_LLINT_S(long long signed int x);

extern long long int __INTCHECK_LLINT_U(long long unsigned int x);

extern long long unsigned int __INTCHECK_ULLINT_S(long long signed int x);

extern long long unsigned int __INTCHECK_ULLINT_U(long long unsigned int x);

extern size_t __INTCHECK_INDEX_S(long long signed int x);

extern size_t __INTCHECK_INDEX_U(long long unsigned int x);

extern size_t __INTCHECK_GETBITLENGTH(long long unsigned int x);

extern long long unsigned int __INTLEFTSHIFT(long long unsigned int op1, long long unsigned int op2);

extern long long unsigned int __INTRIGHTSHIFT(long long unsigned int op1, long long unsigned int op2);

#endif // __INTFIX_ARGYI_H_
