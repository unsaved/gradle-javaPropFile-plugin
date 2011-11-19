JavaPropFile Gradle Plugin

Load Gradle Project with properties from Java properties file.
After loading properties from properties files, the String values are available
like any Gradle Project String property values.

What's wrong with Gradle's "gradle.properties" system?
You are restricted to a single properties in your home directory and one for
each project directory, all with mandated name.
If you want to separate your properties into shared properties and personal
properties, as is a very good practice, you can't.
If you want to set the value of a property to the value of a previously set
property or a system property, or some mix of literals and those, you can't.
If you want to set a Boolean or File value, you will have to change your
Gradle Groovy code to convert.

A sample build is provided in subdirectory "doc".
Even if you don't care to run the demo, you would probably benefit by looking
at the .properties files in there, if not the "build.gradle" file.
If you want to run the example and are pulling this project from Git, then cd
to the doc subdirectory and run "../gradlew" ("..\gradlew" on Windows).  You
do not need to have Gradle installed to run the demonstration.


MOTIVATION

    + Nested definitions like users of Ant and Log4j are used to.
      I.e. use references like ${this} in your property values.
      ${ref} references can be made before or after the 'ref' property is
      defined, as long as the ref is defined in this file (or before this file
      is loaded).
      (The special situation of ${referencing} a property + the referenced
      property was set before loading the current properties file + the
      referenced property value is changed in the current properties file,
      is discussed below).

    + System properties are expanded, like users of Ant, Log4j, and Ivy
      are used to.  May be disabled.

    + Behavior when there are unsatisfied ${references} is configurable.
      You may specify to prohibit (throw), leave the literal as it was,
      replace with empty string, or ignore the property setting altogether.

    + Type-checking.  Fast-fail if an attempt is made to overwrite a Project
      property (or previous property file assignment) with a non-null value
      that is of a different data type.

    + Overwriting is configurable.  You may prohibit(throw), allow, or
      silently ignore attempts to overwrite.

    + System properties may be set by using user-assignable property name
      prefix.

    + Turn on 'typeCasting' and if you prefix or suffix a property name with
      a (ClassName), an instance of that class will be instantiated with a
      valueOf(String) or String constructor using the specified variable value
      as the instantiation parameter.  The resultant object is what will be
      added as a Project property.  Use () to set a null value.


USAGE

Pull plugin from Internet.

    Couldn't be easier.  This will pull the plugin from Maven Central:

        buildscript {
            repositories { mavenCentral() }
            dependencies {
                classpath 'com.admc:gradle-javaPropFile-plugin:latest.milestone'
            }
        }
        apply plugin: 'javaPropFile'
        // Following loads 'app.properties' then 'local.properties' files
        // from project directory if they exist there.
        propFileLoader.traditionalPropertiesInit()
        // See
        // https://github.com/unsaved/gradle-javaPropFile-plugin/tree/master/doc
        // for examples of specifying your own file names and settings,
        // including usage of typeCasting.

Use plugin jar file locally.

    Just use your browser to go to the JavaPropFile directory at Maven
    Central.  http://repo1.maven.org/maven2/com/admc/gradle-javaPropFile-plugin
    Click into the version that you want.
    Right-click and download the only *.jar file in that directory.

    You can save the plugin jar with your project, thereby automatically
    sharing it with other project developers (assuming you use some SCM system).
    Or you can store it in a local directory, perhaps with other Gradle plugin
    jars.  The procedure is the same either way:

        buildscript { dependencies {
            classpath fileTree(
                dir: 'directory/containing/the/plugin/jar/file',
                include: 'gradle-javaPropFile-plugin-*.jar
            )
        } }
        apply plugin: 'javaPropFile'
        // Following loads 'app.properties' then 'local.properties' files
        // from project directory if they exist there.
        propFileLoader.traditionalPropertiesInit()
        // https://github.com/unsaved/gradle-javaPropFile-plugin/tree/master/doc
        // for examples of specifying your own file names and settings,
        // including usage of typeCasting.


DETAILS

    NESTING LIMITATION.  You can't depend on sequence in property files.
    As long as you are not referencing pre-existing-but-changing property
    values, this allows you to nest without concern for sequence.  But if you
    change a property that was set before running load(), you should not use a
    ${reference} to that property in the same build file, because it is not
    guaranteed whether it will expand to the original or changed value.
    Definitely watch for this when changing Gradle-supplied property values
    like 'version' or 'group'.  Therefore, don't nest these.
    If, for example, in a single properties file you want to change the
    'version' and also include the version in another property, you must write
    the literal twice, like:
        version=1.2.3
        title=My Widget, v. 1.3.3
    NOT!!  title=My Widget, v. ${version}
    This is only a concern if the referenced property both pre-exists and is
    changed in the SAME properties file.  If for any reason you really want to
    used nested references using these changing proeprties, then use an
    intermediate variable:
        version=${newVersion}
        title=My Widget, v. ${newVersion}
        newVersion=1.2.3
    (Since sequence-independent, it's ok to reference newVersion before you
    assign a value to it).
    You would also introduce this undesirable state if you assign to the same
    property twice in the same properties file-- so don't do that.

    Precedence works intuitively, not freakishly like Ant properties.
    The value of a variable will be the last value that was assigned to it.
    You can prohibit attempts to overwrite by throwing or silently ignoring.

    Gradle provides no way to unset/remove any Project property, therefore
    JavaPropFile has no capability to remove a property.

    Provided Methods:

        void propFileLoader.load(File)
            Obviously, loads a properties file.  That's the whole point of this
            plugin.

        void propFileLoader.traditionalPropertiesInit()
            Loads 'app.properties' (if it is present), prohibiting use of
            undefined ${...} references; then loads 'local.properties' (if it
            is present), allowing use of undefined references.  Overwriting is
            allowed.  (It will use whatever settings you have made previously
            regarding typeCasting, system property assignment, and system
            property expansion.  Only the last of these is enabled by default).
            File 'build.properties' is actually a more traditional file name
            than 'app.properties' or 'local.properties', but unfortunately
            the name 'build.properties' does not distinguish whether it is
            intended for shared or private/local usage, so I have standardized
            on the distinctive names 'app.properties' and 'local.properties'.

    Configurations:

        After you "apply plugin 'javaPropFile'", you can set the following
        properties on extension object 'propFileLoader'.

        boolean propFileLoader.unsatisfiedRefBehavior
            What to do when ${x} is used in a property value, but 'x' is not
            defined.
            Defaults to com.admc.gradle.THROW, which will cause the property
            file load to fast-fail.
            You can change the behavior to any of the following.  (My syntax
            here assumes that you imported the class
            com.admc.gradle.JavaPropFile).
                JavaPropFile.LITERAL  Leave literal ${x}
                JavaPropFile.EMPTY    Replace ${x} with the empty string
                JavaPropFile.NO_SET   Don't set the property at all
                JavaPropFile.THROW    Here only for completeness.  See above.


        boolean propFileLoader.expandSystemProps
            Whether references to system properties, like ${user.home} are
            expanded.
            Defaults to true.

        boolean propFileLoader.overwriteThrow
            Property file loading will throw and abort immediately if a
            property assignment is attempted to a property that already has a
            value.  (Empty string values and nulls are still values).
            Attempts to assign the same value that a property already has is
            always allowed.
            If this value is true, the value of propFileLoader.overwrite
            doesn't matter.
            Defaults to false

        boolean propFileLoader.overwrite
            If set to true, and .overwriteThrow is false (its default), then
            you can change existing property values.
            Defaults to true.
            If false, and .overwriteThrow is false, then attempts to change
            existing property values are silently ignored.

        String propFileLoader.systemPropPrefix
            If you set it to "sys$", then whenever you set a property with name
            specified as "sys$something", the "sys$" will be stripped off, and
            the Java system property will be set instead of the Project
            property.
            Defaults to null (no System property assignments).

        boolean propFileLoader.typeCasting
            If set to true, then whenever you set a property with name
            beginning or ending with a (ParenthesizedString), the parentheses
            and contents will be stripped off and an instance of the
            specified class name will be instantiated.  Use just () to assign
            a null.  Details about this follow.


TYPE CASTING

Type casting is in effect if you set:  propFileLoader.typeCasting = true

Variable settings with specified name that does not start or end with
parentheses will behave without any type casting, exactly as typeCasting were
off.

Put the name of the desired Java class in parentheses immediately before or
after the property name, with no intervening spaces.
An instance of that class will be instantiated using the specified variable
value as String parameter to either the static valueOf(String) method of the
class, or a String-parameter constructor.

Groovy's rules for package defaults apply.

Some examples:

    output(File)=/path/to/file.txt
    (Long)xferTime.max=31.25
    javac.debug(Boolean)=false
    monitor(com.admc.net.NetworMonitor)=Custom Network Monitor

To assign a null, do the same thing, but give no value at all for class name
nor property value:

    envTarget()=

Type Validation
    If you have JavaPropFile configured to allow property overwriting (by
    default it is allowed), you are still never allowed to directly change a
    variable value from one non-null type to another non-null type.
    If you really want to, you can get around htis constraint by assigning null
    and then assign to the new value.
