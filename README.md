# TRIGGER

TRIGGER provides easy to use area-based triggers for minestom that you can tailor to your use case.

The core implementation is powered by the bundled [QuickHull3D](https://github.com/Quickhull3d/quickhull3d) algorithm.

It is mostly complete and more optimizations and smaller features may be added in the future.
Submit any issues or requests to this GitHub repository.

### Dependencies

- [Minestom](https://github.com/minestom/Minestom/)
- [GSON](https://github.com/google/gson)

## Contact

You may also contact [@catkillsreality](https://discord.com/users/715998925433339978) on [Discord](https://discord.gg),
a response is not guaranteed though.

## Install

The latest version of TRIGGER is available through JitPack:

[![](https://jitpack.io/v/CatKillsReality/TRIGGER.svg?style=flat-square)](https://jitpack.io/#CatKillsReality/TRIGGER)

#### build.gradle (Gradle)

```
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.CatKillsReality:TRIGGER:master-SNAPSHOT'
}
```

#### build.gradle.kts (Gradle Kotlin)

```
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.CatKillsReality:TRIGGER:master-SNAPSHOT")
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
    <version>master-SNAPSHOT</version>
</dependency>
```

Alternatively, just download the source files and throw them into your project.

## Usage

The included javadocs and code comments cover the basic purpose of everything.
If you want to know how something works, take a look at the source code.

### How are shapes defined and how does all of this work?

Simply put, the shape of a trigger is constructed by "shrink wrapping" all corners, which we call **anchors**.

To define a shape of your choice in code, you only need to know its **anchors**, each of which is one of Minestom's **Vec**s, like this:

In [this](#serializing-and-deserializing-triggers-to-and-from-json-with-gson) chapter we will also cover how to load a
previously defined shape from a JSON object using GSON.

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
2D shapes (where all anchors are coplanar, meaning they lie on the same plane) are slightly extruded to create a more
functional trigger.

### TriggerManager

TriggerManager is a utility class for managing a collection of triggers.
It is the main interface that you should interact with.
Standalone use of the Trigger class is not recommended since it does not contain any event hooks.

Begin with creating a new instance:

```
TriggerManager triggers = new TriggerManager(true); // Create a new TriggerManager with debug mode enabled.
```

Beware that enabling debug enables debug rendering, which can cause mostly client, but also some server lag on weaker
machines due to the nature of particles in larger quantities.

It does not add itself as an event handler by default. You should think about who it should apply to, and register it
accordingly.
For simplicity's sake, we'll just register it globally here:

```
MinecraftServer.getGlobalEventHandler().addListener(PlayerMoveEvent.class, triggers);
```

**Make sure that you register the handler AFTER MinecraftServer.init()**

The TriggerManager instance can be created before server init.

### Trigger Event Callbacks

You probably want something to happen when you enter, exit, or just are inside a trigger. That's what the
TriggeredCallback is for.

To get started, create a Consumer of type TriggeredCallback.

The following example demonstrates a simple callback that prints enter and exit messages to chat and sends an actionbar
message every tick that you are inside.

```
Consumer<TriggeredCallback> triggered = callback -> {
            final Player player = callback.player();
            final Trigger trigger = callback.trigger();
            switch (callback.type()) {
                case TICK:
                    player.sendActionBar(Component.text("You are currently inside ").append(trigger.getName()));
                    break;
                case ENTERED:
                    player.sendMessage(Component.text("Entered ").append(trigger.getName()));
                    break;
                case EXITED:
                    player.sendMessage(Component.text("Exited ").append(trigger.getName()));
                    break;
            }
        };
```

The TriggeredCallback record contains the player that triggered the trigger, the trigger itself and a
TriggeredCallback.Type enum that indicates what kind of callback it is.

The enum is structured like this:

```
public enum Type {
        ENTERED,
        EXITED,
        TICK
}
```

ENTERED is called when a player enters the trigger. (Player was not inside last movement tick and now is on the next
tick)

EXITED is called when a player exits the trigger. (Player was inside last movement tick and is now no longer on the next
tick)

TICK is called every tick where a player is inside the trigger. (Player is inside in the current movement tick)

Now, to assign your newly created callback to a trigger, simply pass it in the constructor, as seen
in [the following chapter](#creating-a-trigger).

### Moving Triggers

This system is not designed to handle constantly moving triggers because it checks collision every time the player
moves, and not every game tick.
This is intended and will likely not be changed in the future.

However, triggers can be repositioned:

```
trigger.setPosition();
```

### Creating a trigger

To create a new trigger, simply call `triggers.create()` with triggers being your [TriggerManager](#triggermanager)
instance. and pass it:

- A list of [anchors](#how-are-shapes-defined-and-how-does-all-of-this-work)
- The position where you want to place the trigger in the world (can be moved later on)
- A UUID which has no direct purpose in the current implementation apart from comparing triggers, so just pass
  UUID.random()
- A (text) component that can be used as a display name
- An RGBLike color which is used for debug rendering
- A Consumer of type TriggeredCallback (see [Trigger Event Callbacks](#trigger-event-callbacks))

```
Trigger trigger = triggers.create(cube, new Vec(0, 0, 0), UUID.randomUUID(), Component.text("ExampleTrigger"), NamedTextColor.RED, triggered);
```

### Performance

For optimal performance and mitigation of lag spikes, triggers should only be created during server runtime if strictly
necessary.

The computation speed heavily depends on the amount of anchors and resulting triangles.
To find out how many triangles have been computed and are currently being used, call `triggers.getTotalTriangles()` with
triggers being your [TriggerManager](#triggermanager) instance.

The initial computation happens upon creation. A trigger's anchors may be modified and then recomputed with
`trigger.recompute()`
If the [TriggerManager](#triggermanager) is in debug mode, it will automatically log the last computation time of each
trigger's hull.
To manually request the last computation time in milliseconds of a trigger, call `trigger.getLastComputationTime()`.
Note that this double value is not rounded.

### Serializing and Deserializing Triggers to and from JSON with GSON

This is experimental, but should work in most cases. Report any issues to this repository.

#### Converting a trigger to a JSON string

Keep in mind that this only retains the following data:

- The position of the trigger
- The uncomputed anchors of the trigger's hull
- The Component name

```
String jsonTrigger = trigger.toJSON();
```

#### Creating a trigger from a JSON string

```
Trigger triggerFromJson = Trigger.fromJSON(jsonString);
```

Keep in mind that this only loads the position, anchors and name of the trigger.

- The UUID is randomly chosen through `UUID.random()`.
- The RGBLike debug rendering color defaults to `NamedTextColor.RED`, but can be set using `trigger.setColor()`.
- The [TriggeredCallback](#trigger-event-callbacks) defaults to logging an error message if the callback is not replaced

#### Setting the proper callback

```
trigger.setTriggeredCallback(callback);
```

**Do NOT forget to add this new trigger to your [TriggerManager](#triggermanager) instance** like this:

```
triggers.add(triggerFromJson);
```

# That's it for now. Report any issues, questions and other things regarding this project to this repo or to [me](#contact) directly. Enjoy.

