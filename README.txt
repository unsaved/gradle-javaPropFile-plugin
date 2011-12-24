JavaPropFile Gradle Plugin


Version 0.4.* and later require Gradle 1.0-milestone-6 or later.
IMPORTANT:  User who are upgrading Gradle or upgrading JavaPropFile should read
the file "upgrade.txt" in the "doc" subdirectory.

This plugin is for loading a Map, a Gradle Project or extension object (and
objects nested beneath them), with properties from Java properties file.
Most classes are supported, so that you can write JDK objects, custom objects,
array, and collections in addition to String values (using a simply 'casting'
syntax like "file.txt(File)").
Several mechanisms provided make it easy use a differentiated property name
space for propery file loads (when your use case allows for this).

What's wrong with Gradle's "gradle.properties" system?
You are restricted to a single properties in your home directory and one for
each project directory, all with mandated name.
If you want to separate your properties into shared properties and personal
properties, as is a very good practice, you can't.
If you want to set the value of a property based on anything else (like a
previously set property, an extension object property value, a system property,
or some nested item in your Project), you can't.
If you want to set even a basic JDK object like a Boolean or File value, you
will have to change your Gradle Groovy code to convert from the String property
value.
If you want to load or merge new properties from a properties file into an
exiting map, perhaps automatically prefixing these properties with a
distinguishing prefix, you have to do that manually.
If you want to do all of these things at once... settle down for a week of
coding.

A sample build setup is provided in subdirectory "doc".
Even if you don't care to run the demo, you would probably benefit by looking
at the .properties files in there, if not the "build.gradle" file.
If you want to run the example and are pulling this project from Git, then cd
to the doc subdirectory and run "../gradlew" ("..\gradlew" on Windows).  You
do not need to have Gradle installed to run the demonstration (if you pull the
entire project from Git).


FEATURES

    + Nested property definitions like users of Ant and Log4j are used to.
      I.e. use references like ${this} in your property values.
      ${ref} references can be made before or after the 'ref' property is
      defined, as long as the ref is defined in this file (or before this file
      is loaded).  (If writing an object and a property of the object, you
      must create the object first).

    + System properties are expanded, like users of Ant, Log4j, and Ivy
      are used to, but with their own (user-specified) namespace prefix to
      avoid name collisions.  (May be disabled).

    + System properties may be set using the same prefix.  (May be disabled).

    + Behavior when there are unsatisfied ${references} is configurable.
      You may specify to prohibit (throw), leave the literal as it was,
      replace with empty string, or ignore the property setting altogether.

    + Type-checking.  Fast-fail if an attempt is made to overwrite a Project
      property (or previous property file assignment) with a non-null value
      that is of a different data type.

    + Overwriting is configurable.  You may prohibit(throw), allow, or
      silently ignore attempts to overwrite.

    + Turn on 'typeCasting' and if you prefix or suffix a property name with
      a (ClassName), an instance of that class will be instantiated with a
      valueOf(String) or String constructor using the specified property value
      as the instantiation parameter.  The resultant object is what will be
      added as a Project property.  Use () to set a null value.

    + typeCasting supports arrays and arbitrary Collection types

    + '.' may be used both as a character in a property name like
          xfer.max(Float)=30.0
      and to dereference object properties, like:
          compileJava.options.debug(Boolean) = false
      The behavior is toggled by whether or not the property file definition
      has a space after the key.  Note the space before "=" above.

    + Properties of either the Gradle project or specified extension object may
      be set, as well as descendant objects (using the . dereference operator
      of previous bullet).  By default, setting or referencing "x" means
      property "x" of the Gradle Project, but you can precede the property name
      with extension object name + $, like "ivyxml$depFile".

    + Entire property files may be dedicated to configuring a specified
      extension object.  I.e. if the target extension object is specified as
      optional load parameter, properties will default to that object instead
      of to the Project.

    + Extension object properties may be set before the target extension object
      becomes available.  Settings deferred in this way will be applied as soon
      as the extension object comes online.  Deferral behavior may  be disabled
      if you want to enforce that target extension objects are available ahead
      of time.

    + User specifiable load-specific automatic prefix.  This allows you to
      load properties from multiple sources into your Project or Map, without
      risking name collisions, and being able to clearly and definitively tell
      the source of each property.

    + Individual ${references} may use property name prefix characters to
      override the unset-reference-property behavior, so that in a single
      properties file or String or text file to be expanded, some references
      may be required and others optional.  THIS BEHAVIOR IS FULLY IMPLEMENTED
      AND TESTED IN THE SOURCE CODE REPOSITORY, BUT ARE NOT IN A PUBLIC RELEASE
      YET.

CONSTRAINT
    Property names references may not begin the following characters ! - .
    E.g., you may define a property with name "!varName", but you can't
    reference it with ${!varName}.

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

        // Create a new Map by loading a properties file:
        someMap = propFileLoader(load(file('mail.properties'), [:])

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

        // Create a new Map by loading a properties file:
        someMap = propFileLoader(load(file('mail.properties'), [:])


DETAILS

    IMPORTANT!  KEY/VALUE DELIMITER
    Java properties files allow for the key to be terminated by equal sign,
    colon character, or a white space character.  (Our keys contain a
    property and may contain other typing and parent-object-identifying
    information).
    Since white-space is optional after the property keys, and since property
    file rules even allow for the '=' to follow white space, we use white space
    in this position to toggle '.'-as-dereference-operator behavior.
    Compare:
        x.a=eks
        # No whitespace immediately after key "x.a" so property name is "x.a".
    vs.
        x.a =eks
        # Whitespace immediately after key "x.a" so this means proeprty "a" of
        # object "x".
    (MNEMONIC:  Groovy is distinctive for allowing white space to delimit
    between method name and parameters.  We use white space as delimiter to
    trigger Groovy-style dereferencing.)
    The presence of absence of white space after the key enables or disables
    '.'-as-dereference-operator both for the key and for ${references" in the
    value.  Another example to show this:
        x=Value includes a reference to ${property.with.name.containg.dots}
    vs.
        x =Value includes a reference to ${subObject1.subObject2.propertyName}

    REFERENCE SYNTAX
    References are not allowed on the left-hand side of property file records.
    They may be used on the value (right-hand) side of property file records,
    and in Strings and Files that are expand()ed.
    These are the variants:
        ${propName}          # Simple property 'propName' of the target
                             # object (which defaults to the Gradle Project).
        ${sys|propName}      # For Java system property 'propName', using
                             # default systemPropPrefix of 'sys|'.
        ${extObj$propName}   # Property 'propName' of extension object 'extObj'
        ${obj1.obj2.propName}  # If '.'-as deference operator is not active,
                               # then this is just property
                               # 'obj1.obj2.propName' of the target object
                               # like the first case above.
                               # If '.'-as deference operator is active,
                               # then this is property 'projName' of property
                               # obj2 of property obj1 or the target object.
                               # In the second case, it is an error to
                               # reference a non-defined property.
        # In all cases above (except nested properties, as noted), references
        # to properties that are missing are handled according to the default
        # or specified unsatisfiedRefBehavior.
       THIS FOLLOWING ALTERNATIVES ARE FULLY IMPLEMENTED AND TESTED IN THE
       SOURCE CODE REPOSITORY, BUT ARE NOT IN A PUBLIC RELEASE YET.
        ${!propName}         # For all variants above, if the reference
        ${!sys|propName}     # property name is immediately preceded by !,
        etc.                 # then the unsatisfiedRefBehavior is thereby
                             # overridden so that if the referenced property
                             # is not set, JavaPropFile wil throw.
        ${-propName}, etc.   # Just like previous case, but if the property is
                             # not set, the ${-...} expression will be replace
                             # with nothing at all (an empty string).
        ${.propName}, etc.   # Just like previous case, but if the proeprty is
                             # not set, the ${....} expression will be left
                             # exactly as it is.
        # The !, -, . prefixes, which override unsatisfiedRefBehavior, are
        # known as 'behaviorRefPrefixes'.

    SEQUENCE OF ASSIGNMENTS AND REFERENCES
    In all cases, sequence is consistent and understandable.
    According to Java's property file rules, it is useless to assign to a
    single property twice in a single properties file.  We therefore prohibit
    this confusing and misleading scenario.
    In the great majority of cases, sequence makes no difference.  You can
    assign as many properties as you wish to, and reference as many properties
    as you wish to, and as long as the following two situations do not apply,
    the sequence doesn't matter.  The ${reference} value of property 'x' will
    be precisely what 'x' is set to in the same file (or, if it is not set in
    this file, then what it was before the file was loaded).  If 'x' is
    referenced 100 times like ${x}, it will have the same value every time,
    regardless of whether it is referenced before or after 'x' is assigned.

        Exception 1:  If property 'x' has a value before loading a property
        file that assigns 'x', then sequence matters, because ${x} will
        resolve to the original value before the assignment but to the new
        value after the assignment.

        Exception 2:  You can't assign to an object before the object is
        present.  The sequence must be like this:
            t1(Thread)=one
            t1.name=two

    ESCAPING
    You escape with backslash \, whether escaping is required by Java
    properties file format, java.util.regex.Pattern format, or JavaPropFile.
    As is very well documented at
    http://docs.oracle.com/javase/6/docs/api/java/util/Properties.html#load(java.io.Reader)
    you must \ to escape all white space characters (including line breaks),
    ':', and '=' inside of property keys.
        colon\:and\ space_inside_property_key=hello
    But to tell JavaPropFile to escape characters (list of examples follows),
    you must double escape with \\, because that is how you tell any Java
    properties file to pass | to the application (and in this context,
    JavaPropFile is an application).  Examples:
        # A literal | in a regex must be escaped, and you must use \\ to get
        # \ to JavaPropFile:
        mockBean$intList(Integer[\\|]ArrayList)=91|72|101
        # The ' ' here does not need to be escaped in a regex, but does need
        # to be escaped in a properties file key or the key would end at the
        # space:
        alpha(Integer[\ ]ArrayList)=94 3 12
        expressionVar=Value will contain literal \\${dollar sign and curlies}
        enableX\\(Boolean\\)=With typeCasting on you need this for property  \
                             name "enableX(Boolean)".
        # With no space after the key, no need to escape dots in keys and refs.
        owner.id=x5klh.umagumma
        # But if you want to use '.'-as-dereference-operator for only part of
        # the definition, you will need to escape:
        owner\\.id=x5klh.${plugBean.user.name}
        Property names (not values) may not begin the following characters
        ! - .
        Characters  $ ( ) } in property names must be escaped with \\.


    Precedence works intuitively, not freakishly like Ant properties.
    The value of a property will be the last value that was assigned to it.
    You can prohibit attempts to overwrite by throwing or silently ignoring.

    Gradle provides no way to unset/remove any Project property, therefore
    JavaPropFile has no capability to remove a property.

    Provided Public Methods:

        void propFileLoader.load(File propertiesFile)
            Loads a properties file and writes Gradle Project properties with
            those values.

        void propFileLoader.load(File propertiesFile, String keyAssignPrefix)
            Exact same as previous, except that each property written to the
            Project is prefixed with the supplied String.
            If you ran "propFileLoader.load(file('a.properties'), 'pref')" and
            you have a line in "a.properties" like:
                key=val
            then JavaPropFile would end up doing
                gradleProject.setProperty('prefkey', 'val)

        Map propFileLoader.load(File propertiesFile, Map aMap)
            Loads a properties file and writes map properties with those values.
            You must specify a Map to be populated, so if you want to load a
            new Map, just specify value [:] as the map.
            The given 'aMap' reference will be returned.

        void propFileLoader.load(
                File propertiesFile, String keyAssignPrefix, Map aMap)
            Exact same as previous, except that each property written to the
            Map is prefixed with the supplied String.  See description for the
            load(File, String) method above for details about how the prefix
            value is applied.

        void propFileLoader.loadIntoExensionObject(
                File, String defaultExtObjName)
            The String is an extension object name.  This is the default object
            to apply property values to (or with as-dereference-operator, this
            is where the dereferencing begins).

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

        Map<String, Map<String, Object>>
                propFileLoader.getDeferredExtensionProps()
            Most users will not use this.  It is for checking the state of
            deferred extension object properties.
            Returns a map of extension object names to map of deferred
            property definitions for that extension object.

        static Class JavaPropFile.resolveClass(String className)
            This probably won't be called in the context of using JavaPropFile,
            but some developers may want to copy this, as it successfully
            uses the context class loader and Groovy's default package rules
            to resolve class names.

        void propFileLoader.executeDeferrals()
            Most users will not use this.  It is invoked automatically by a
            Gradle callback to execute deferred extension property settings
            when the target extension object comes online.

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
            unsatisfiedRef behavior does not apply to references to missing
             extension object references like ${x$y} (on the value side of
             assignments or in expand() Strings/files).  This situation will
             always throw.  The only reason for this exception is the amount
             of coding and complexity needed for a capability with unknown
             user demand.

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
            If this is non-null, then you can both reference system properties
            in property definition values, and can assign system properties.
            An example of each with systemPropPrefix set to 'sys|':
                projectOwner=Mr. ${sys|user.name}
                sys|java.io.tmpdir=/usr/local/tmp
            Set to null to prevent referencing and assignment of system
            properties.
            Defaults to 'sys|'.

        boolean propFileLoader.typeCasting
            If set to true, then whenever you set a property with name
            beginning or ending with a (ParenthesizedString), the parentheses
            and contents will be stripped off and an instance of the
            specified class name will be instantiated.  Use just () to assign
            a null.  Details about this follow.

        boolean propFileLoader.defer
            If false then assignments to missing extension objects will cause
            the property file load to fail.
            If true, then assignments for missing extensions will be deferred
            until the target extension objects become available.
            Defaults to true.


TYPE CASTING

Type casting is in effect if you set:  propFileLoader.typeCasting = true

Property settings with specified name that does not start or end with
parentheses will behave without any type casting, exactly as if typeCasting
were off.

Put the name of the desired Java class in parentheses immediately before or
after the property name, with no intervening spaces.
An instance of that class will be instantiated using the specified property
value as String parameter to either the static valueOf(String) method of the
class, or a String-parameter constructor.

Groovy's rules for package defaults apply.

Some examples:

    output(File)=/path/to/file.txt
    (Long)xferTime.max=31.25
    compileJava.options.debug(Boolean) = false
    monitor(com.admc.net.NetworMonitor)=Custom Network Monitor
    mavenRepository.dest.url(URL)=file:/tmp/fakeMvn

To assign a null, do the same thing, but give no value at all for class name
nor property value:

    envTarget()=

Arrays and Collections

    Immediately after the element typeCasting class,
    add [ + splitting-pattern + ] + optional-CollectionImplementationClass.
    The Splitting pattern is a java.util.regex.Pattern String, and you must,
    of course, follow Java properties file escaping rules.  If no collection
    implementation class is specifies, an Array will be instantiated.
    Examples:
        mockBean$intList(Integer[\\|]ArrayList)=91|72|101
        # Note the extra ugly backslashes needed to satisfy both Java
        # properties escaping and java.util.regex.Pattern escaping for '|'.
        alpha(Integer[\ ]ArrayList)=94 3 12
        alpha(String[,]HashSet)=one,two,three

Type Validation
    If you have JavaPropFile configured to allow property overwriting (by
    default it is allowed), you are still never allowed to directly change a
    property value from one non-null type to another non-null type.
    If you really want to, you can get around htis constraint by assigning null
    and then assign to the new value.
