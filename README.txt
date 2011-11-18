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

MOTIVATION

    + Nested definitions like users of Ant and Log4j are used to.
      (I.e. use references like ${this} in your property values.
      If setting are new (i.e. not changing existing properties), you can
      reference properties before they are set.

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
            mavenCentral()
            dependencies {
                classpath 'com.admc:gradle-javaPropFile-plugin:latest.milestone'
            }
        }
        apply plugin 'ivyxml'
        import com.admc.gradle.JavaPropFile
        ...
        // Load any properties files that you want to.
        // propFileLoader settings will effect following .loads.
        propFileLoader.overwriteThrow = true
        propFileLoader.load(file('build.properties')  // Shared properties file

        propFileLoader.overwriteThrow = false
        propFileLoader.unsatisfiedRefBehavior = JavaPropFile.Behavior.NO_SET
        propFileLoader.systemPropPrefix = 'sys$'
        propFileLoader.load(file('local.properties')  // Personal prop file

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
        apply plugin 'ivyxml'
        import com.admc.gradle.JavaPropFile
        ...
        // Load any properties files that you want to.
        // propFileLoader settings will effect following .loads.
        propFileLoader.overwriteThrow = true
        propFileLoader.load(file('build.properties')  // Shared properties file

        propFileLoader.overwriteThrow = false
        propFileLoader.unsatisfiedRefBehavior = JavaPropFile.Behavior.NO_SET
        propFileLoader.systemPropPrefix = 'sys$'
        propFileLoader.load(file('local.properties')  // Personal prop file


DETAILS

    Precedence works intuitively, not freakishly like Ant properties.
    The value of a variable will be the last value that was assigned to it.
    You can prohibit attempts to overwrite by throwing or silently ignoring.

    The provided method 'propFileLoader.traditionalPropertiesInit()'
    loads 'app.properties' (if it is present), prohibiting use of undefined
    ${...} references and prohibiting property overwriting;, then loads
    'local.properties' (if it is present), allowing use of undefined
    references and property overwriting.  (It will use whatever settings you
    have made previously regarding typeCasting, system property assignment,
    and system property expansion.  Only the last of these is enabled by
    default).

    Gradle provides no way to unset/remove any Project property, therefore
    JavaPropFile has no capability to remove a property.

    Configurations:

        After you "apply plugin 'ivyxml'", you can set the following properties
        on extension object 'propFileLoader'.

        boolean propFileLoader.unsatisfiedRefBehavior
            What to do when ${x} is used in a property value, but 'x' is not
            defined.  Defaults to com.admc.gradle.THROW, which will cause
            the property file load to fast-fail.
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
    monitor(com.admc.net.NetworMonitor)=Custom Network Monitor

To assign a null, do the same thing, but give no value at all for class name
nor property value:

    envTarget()=

If you have JavaPropFile configured to allow property overwriting (by default
it is allowed), you are still never allowed to directly change a variable value
from one non-null type to another non-null type.
If you really want to, you can assign to null and then assign to the new value.
