# TRIGGER
TRIGGER provides easy to use area-based triggers for minestom that you can tailor to your use case.

The core implementation is powered by the bundled [QuickHull3D](https://github.com/Quickhull3d/quickhull3d) algorithm.

It is mostly complete and more optimizations and smaller features may be added in the future.
Submit any issues or requests to this GitHub repository.

You may also contact [@catkillsreality](https://discord.com/users/715998925433339978) on [Discord](https://discord.gg), a response is not guaranteed though.

### Dependencies

- [Minestom](https://github.com/minestom/Minestom/)
- [GSON](https://github.com/google/gson)

## Install

TRIGGER is available through JitPack:

[![](https://jitpack.io/v/CatKillsReality/TRIGGER.svg?style=flat-square)](https://jitpack.io/#CatKillsReality/TRIGGER) << Replace TAG below with this commmit/version hash.

#### build.gradle (Gradle)
```
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    // Make sure to replace TAG with the commit/version has above
    implementation 'com.github.CatKillsReality:TRIGGER:TAG'
}
```

#### build.gradle.kts (Gradle Kotlin)
```
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // Make sure to replace TAG with the commit/version has above
    implementation("com.github.CatKillsReality:TRIGGER:TAG")
}
```

#### pom.xml (Maven)
```
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.CatKillsReality</groupId>
    <artifactId>TRIGGER</artifactId>
    <version>TAG</version> // Make sure to replace TAG with the commit/version has above
</dependency>
```


Alternatively, just download the source files and throw them into your project.

## Usage

### How are shapes defined and how does all of this work?
Simply put, the shape of a trigger is constructed by "shrink wrapping" all corners, which we call **anchors**.

To define a shape of your choice in code, you only need to know its **anchors**, each of which is one of Minestom's **Vec**s, like this:

In [this](#serializing-and-deserializing-triggers-to-and-from-json-with-gson) chapter we will also cover how to load a previously defined shape from a JSON object using GSON.

```
// A one block large cube
List<Vec> cube = List.of(
    new Vec(0, 0, 0),
    new Vec(2, 0, 0),
    new Vec(2, 2, 0),
    new Vec(0, 2, 0),
    new Vec(0, 0, 2),
    new Vec(2, 0, 2),
    new Vec(2, 2, 2),
    new Vec(0, 2, 2)
);
```

This is done by the QuickHull3D algorithm.
The resulting triangles make up the shape and are used for the collision detection and debug rendering.
Collision detection is based on the Separating Axis Theorem (SAT).
2D shapes (where all anchors are coplanar, meaning they lie on the same plane) are slightly extruded to create a more functional trigger.


### TriggerManager
TriggerManager is a  utility class for managing a collection of triggers.
Standalone use of the Trigger class is not recommended since it does not contain any event hooks.

Begin with creating a new instance:
```
TriggerManager triggers = new TriggerManager(true); // Create a new TriggerManager with debug mode enabled.
```

### Serializing and Deserializing Triggers to and from JSON with GSON

# These docs are unfinished. I will finsish them once I have the time.

