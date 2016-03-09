CC=gcc
SLINK_FLAGS=-shared
LINK_OUT_FLAG=-o
CC_OUT_FLAG=-c
CC_EXTRA_FLAG=-Wall -fPIC
SO_EXT=.so
PREFIX=/usr

all: libargyi.so

libargyi.so: argyi.o
	$(CC) $(SLINK_FLAGS) $(LINK_OUT_FLAG) $@ $^

argyi.o: argyi.c
	$(CC) $(CC_OUT_FLAG) $(CC_EXTRA_FLAG) $^

clean:
	@rm -f *.o *$(SO_EXT)

install:
	@mkdir -p $(PREFIX)/include
	@mkdir -p $(PREFIX)/lib
	@cp ./argyi.h $(PREFIX)/include/argyi.h
	@cp ./libargyi$(SO_EXT) $(PREFIX)/lib/libargyi$(SO_EXT)
	@echo argyi was successfully installed.

uninstall:
	@rm -f $(PREFIX)/include/argyi.h
	@rm -f $(PREFIX)/lib/libargyi$(SO_EXT)
	@echo argyi was successfully uninstalled.
