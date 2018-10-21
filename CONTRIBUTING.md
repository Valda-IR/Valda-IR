Contributing
============

This document is WIP.

We roughly follow the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) except with 4 spaces indent instead of 2.

This project uses [TestNG](https://testng.org/doc/index.html) for unit testing, as opposed to jUnit. This is mostly because of data provider support. If you aren't familiar with TestNG, the most important thing to be aware of is that assertion parameter order is reversed (testng: `assertEquals(actual, expected)`, junit: `assertEquals(expected, actual)`).

Testing with ART
----------------

Testing outside of a VM is useful, but ART is pretty difficult to build.

- Install the requirements listed on https://source.android.com/setup/requirements . Running inside a VM / docker container with ubuntu 16.04 may be useful
- Install `repo` as shown here: https://source.android.com/setup/downloading
- Choose the master-art branch ( https://android.googlesource.com/platform/manifest/+/master-art )
- `repo sync`, this needs lots of disk space
- setup proprietary binaries as shown in https://source.android.com/setup/building . Not sure if this is actually required but I did this
- `source build/envsetup.sh`
- `lunch aosp_x86_64-eng`
- `make SOONG_ALLOW_MISSING_DEPENDENCIES=true build-art-host`
- At some point during this command, the `art` runtime will be built in `out/target/product/generic_x86_64/system/bin`.

When running the unit tests, set the `ART_RUNTIME` environment variable to the `art` binary to run ART tests (mostly for stuff like dex compilation and verification). 