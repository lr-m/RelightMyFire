#include <stdio.h>

/* Runs before main() via constructor priority.
   Forces line-unbuffered stdout/stderr so prints appear
   in the app even when stdout is a pipe, not a TTY. */
__attribute__((constructor))
static void unbuf(void) {
    setvbuf(stdout, NULL, _IONBF, 0);
    setvbuf(stderr, NULL, _IONBF, 0);
}
