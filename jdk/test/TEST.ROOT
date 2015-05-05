# This file identifies the root of the test-suite hierarchy.
# It also contains test-suite configuration information.

# The list of keywords supported in the entire test suite.  The
# "intermittent" keyword marks tests known to fail intermittently.
# The "randomness" keyword marks tests using randomness with test
# cases differing from run to run. (A test using a fixed random seed
# would not count as "randomness" by this definition.) Extra care
# should be taken to handle test failures of intermittent or
# randomness tests.

keys=2d dnd i18n intermittent randomness

# Tests that must run in othervm mode
othervm.dirs=java/awt java/beans javax/accessibility javax/imageio javax/sound javax/print javax/management com/sun/awt sun/awt sun/java2d sun/pisces javax/xml/jaxp/testng/validation

# Tests that cannot run concurrently
exclusiveAccess.dirs=java/rmi/Naming java/util/prefs sun/management/jmxremote sun/tools/jstatd sun/security/mscapi java/util/stream javax/rmi

# Group definitions
groups=TEST.groups [closed/TEST.groups]

# Tests using jtreg 4.1 b10 features
requiredVersion=4.1 b10
