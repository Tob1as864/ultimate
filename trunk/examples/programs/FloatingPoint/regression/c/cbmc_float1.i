//#Safe

/*
 * Taken from CBMC's regression test suite
 * (http://svn.cprover.org/svn/cbmc/trunk/regression/cbmc/).
 *
 * The overflow checks were omitted as these require more elaborate assertions.
 */

 extern void __VERIFIER_error(void);
int main() {
  double x;
  int y;

  x=2;
  x-=0.6;
  y=x;

  if(!(y==1)) __VERIFIER_error();

  x=2;
  x-=0.4;
  y=x;


  if(!(y==1)) __VERIFIER_error();
}
