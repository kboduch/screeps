# Screeps Kotlin

### Code upload

Credentials must be provided in a `gradle.properties` file in the root folder of the project.
    
    screepsUser=<your-username>
    screepsPassword=<your-password>
    screepsHost=https://screeps.com (optional)
    screepsBranch=main (optional)

Alternatively, you can set up an [auth token](https://screeps.com/a/#!/account/auth-tokens) instead of a password (only for official servers)

    screepsToken=<your-token>
    screepsHost=https://screeps.com (optional)
    screepsBranch=main (optional)
    
Usage:

    ./gradlew deploy

### Types
Standalone types are available here: https://github.com/exaV/screeps-kotlin-types

### Performance
Kotlin compiles to plain javascript, similar to Typescript. There is no runtime overhead.
The major difference is that kotlin ships with a separate 1.5MB standard library. We recommend to use the the Dead-Code-Elimination-Plugin 'kotlin-dce-js', like this project does, to drastically reduce the size of all dependencies (e.g. stdlib is 180kb afterwards).

### A note on `Object`
Kotlin's `Object` Singletons persist over multiple ticks. 
This can be very useful to store non-essential but expensive-to-calculate data, especially in combination with `lazy()`
