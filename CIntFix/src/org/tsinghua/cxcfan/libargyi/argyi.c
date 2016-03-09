#include <stdio.h>
#include <stdlib.h>
#include <limits.h>
#include <stdint.h>

#define __INTCHECK_INDET_VALUE 0

void __INTCHECK_ERROR(const char * errmsg)
{
	fputs(errmsg, stderr);
	exit(1);
}

int __INTCHECK_INT_S(long long signed int x)
{
	int y = (int)x;
	if(x == (long long signed int)y)
	{
		return y;
	}
	else
	{
		__INTCHECK_ERROR("Error!(Failed in: __INTCHECK_INT_S)\n");
		return __INTCHECK_INDET_VALUE;
	}
}

int __INTCHECK_INT_U(long long unsigned int x)
{
	int y = (int)x;
	if(x == (long long unsigned int)y)
	{
		return y;
	}
	else
	{
		__INTCHECK_ERROR("Error!(Failed in: __INTCHECK_INT_U)\n");
		return __INTCHECK_INDET_VALUE;
	}
}

unsigned int __INTCHECK_UINT_S(long long signed int x)
{
	unsigned int y = (unsigned int)x;
	if(x == (long long signed int)y)
	{
		return y;
	}
	else
	{
		__INTCHECK_ERROR("Error!(Failed in: __INTCHECK_UINT_S)\n");
		return __INTCHECK_INDET_VALUE;
	}
}

unsigned int __INTCHECK_UINT_U(long long unsigned int x)
{
	unsigned int y = (unsigned int)x;
	if(x == (long long unsigned int)y)
	{
		return y;
	}
	else
	{
		__INTCHECK_ERROR("Error!(Failed in: __INTCHECK_UINT_U)\n");
		return __INTCHECK_INDET_VALUE;
	}
}

short __INTCHECK_SHORT_S(long long signed int x)
{
	short y = (short)x;
	if(x == (long long signed int)y)
	{
		return y;
	}
	else
	{
		__INTCHECK_ERROR("Error!(Failed in: __INTCHECK_SHORT_S)\n");
		return __INTCHECK_INDET_VALUE;
	}
}

short __INTCHECK_SHORT_U(long long unsigned int x)
{
	short y = (short)x;
	if(x == (long long unsigned int)y)
	{
		return y;
	}
	else
	{
		__INTCHECK_ERROR("Error!(Failed in: __INTCHECK_SHORT_U)\n");
		return __INTCHECK_INDET_VALUE;
	}
}

unsigned short __INTCHECK_USHORT_S(long long signed int x)
{
	unsigned short y = (unsigned short)x;
	if(x == (long long signed int)y)
	{
		return y;
	}
	else
	{
		__INTCHECK_ERROR("Error!(Failed in: __INTCHECK_USHORT_S)\n");
		return __INTCHECK_INDET_VALUE;
	}
}

unsigned short __INTCHECK_USHORT_U(long long unsigned int x)
{
	unsigned short y = (unsigned short)x;
	if(x == (long long unsigned int)y)
	{
		return y;
	}
	else
	{
		__INTCHECK_ERROR("Error!(Failed in: __INTCHECK_USHORT_U)\n");
		return __INTCHECK_INDET_VALUE;
	}
}

/* The signedness of char is platform-dependent. */
signed char __INTCHECK_CHAR_S(long long signed int x)
{
	signed char y = (signed char)x;
	if(x == (long long signed int)y)
	{
		return y;
	}
	else
	{
		__INTCHECK_ERROR("Error!(Failed in: __INTCHECK_CHAR_S)\n");
		return __INTCHECK_INDET_VALUE;
	}
}

signed char __INTCHECK_CHAR_U(long long unsigned int x)
{
	signed char y = (signed char)x;
	if(x == (long long unsigned int)y)
	{
		return y;
	}
	else
	{
		__INTCHECK_ERROR("Error!(Failed in: __INTCHECK_CHAR_U)\n");
		return __INTCHECK_INDET_VALUE;
	}
}

unsigned char __INTCHECK_UCHAR_S(long long signed int x)
{
	unsigned char y = (unsigned char)x;
	if(x == (long long signed int)y)
	{
		return y;
	}
	else
	{
		__INTCHECK_ERROR("Error!(Failed in: __INTCHECK_UCHAR_S)\n");
		return __INTCHECK_INDET_VALUE;
	}
}

unsigned char __INTCHECK_UCHAR_U(long long unsigned int x)
{
	unsigned char y = (unsigned char)x;
	if(x == (long long unsigned int)y)
	{
		return y;
	}
	else
	{
		__INTCHECK_ERROR("Error!(Failed in: __INTCHECK_UCHAR_U)\n");
		return __INTCHECK_INDET_VALUE;
	}
}

long int __INTCHECK_LINT_S(long long signed int x)
{
	long int y = (long int)x;
	if(x == (long long signed int)y)
	{
		return y;
	}
	else
	{
		__INTCHECK_ERROR("Error!(Failed in: __INTCHECK_LINT_S)\n");
		return __INTCHECK_INDET_VALUE;
	}
}

long int __INTCHECK_LINT_U(long long unsigned int x)
{
	if(x <= LONG_MAX)
	{
		return (long int)x;
	}
	else
	{
		__INTCHECK_ERROR("Error!(Failed in: __INTCHECK_LINT_U)\n");
		return __INTCHECK_INDET_VALUE;
	}
}

long unsigned int __INTCHECK_ULINT_S(long long signed int x)
{
	if(x >= 0)
	{
		return (long unsigned int)x;
	}
	else
	{
		__INTCHECK_ERROR("Error!(Failed in: __INTCHECK_ULINT_S)\n");
		return __INTCHECK_INDET_VALUE;
	}
}

long unsigned int __INTCHECK_ULINT_U(long long unsigned int x)
{
	long unsigned int y = (long unsigned int)x;
	if(x == (long long unsigned int)y)
	{
		return y;
	}
	else
	{
		__INTCHECK_ERROR("Error!(Failed in: __INTCHECK_ULINT_U)\n");
		return __INTCHECK_INDET_VALUE;
	}
}

long long int __INTCHECK_LLINT_S(long long signed int x)
{
	return x;
}

long long int __INTCHECK_LLINT_U(long long unsigned int x)
{
	if(x <= LLONG_MAX)
	{
		return (long long signed int)x;
	}
	else
	{
		__INTCHECK_ERROR("Error!(Failed in: __INTCHECK_LLINT_U)\n");
		return __INTCHECK_INDET_VALUE;
	}
}

long long unsigned int __INTCHECK_ULLINT_S(long long signed int x)
{
	if(x >= 0)
	{
		return (long long unsigned int)x;
	}
	else
	{
		__INTCHECK_ERROR("Error!(Failed in: __INTCHECK_ULLINT_S)\n");
		return __INTCHECK_INDET_VALUE;
	}
}

long long unsigned int __INTCHECK_ULLINT_U(long long unsigned int x)
{
	return x;
}

/* In pre-processed code, the array expression of array subscript can only be array
 * If the base is a pointer, the expression is automatically transformed into binary expression */
size_t __INTCHECK_INDEX_S(long long signed int x)
{
	/* SIZE_MAX is defined in stdint.h, not in limits.h */
	if(x >= 0 && x <= SIZE_MAX)
	{
		return (size_t)x;
	}
	else
	{
		__INTCHECK_ERROR("Error!(Failed in: __INTCHECK_INDEX_S)\n");
		return __INTCHECK_INDET_VALUE;
	}
}

size_t __INTCHECK_INDEX_U(long long unsigned int x)
{
	if(x <= SIZE_MAX)
	{
		return (size_t)x;
	}
	else
	{
		__INTCHECK_ERROR("Error!(Failed in: __INTCHECK_INDEX_U)\n");
		return __INTCHECK_INDET_VALUE;
	}
}

size_t __INTCHECK_GETBITLENGTH(long long unsigned int x)
{
	size_t len = sizeof(long long unsigned int) * 8;
	long long unsigned int mask = (long long unsigned int)1 << (len - 1);
	while(len > 0)
	{
		if((x & mask) != 0)
		{
			break;
		}
		mask |= (mask >> 1);
		len--;
	}
	return (len);
}

/* bit-shift operation should be handled carefully since it tends to have undefined behaviors */
long long unsigned int __INTLEFTSHIFT(long long unsigned int op1, long long unsigned int op2)
{
	size_t bitLength = __INTCHECK_GETBITLENGTH(op1);
    if(bitLength != 0)
	{
		size_t allowShiftBit = sizeof(long long unsigned int) * 8 - bitLength;
		if(op2 > (long long unsigned int)allowShiftBit)
		{
			__INTCHECK_ERROR("Error!(Failed in: __INTLEFTSHIFT)\n");
			return __INTCHECK_INDET_VALUE;
		}
		return (op1 << op2);
	}
	else
	{
		return 0;
	}
}

/* If op1 has a signed type and a negative value, the resulting value is implementation-defined. */
long long unsigned int __INTRIGHTSHIFT(long long unsigned int op1, long long unsigned int op2)
{
	size_t bitLength = __INTCHECK_GETBITLENGTH(op1);
	if(op2 <= (long long unsigned int)bitLength)
	{
		return (op1 >> op2);
	}
	else
	{
		return 0;
	}
}
