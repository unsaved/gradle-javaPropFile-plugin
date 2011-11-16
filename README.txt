JavaPropFile Gradle Plugin

Load Gradle Project with properties from Java properties file.

This isn't simple loading like you could do with 3 lines of Groovy.
What's so great?

    + Nested definitions like users of Ant and Log4j are used to.
      Nesting does not have to be in forward order.

    + System properties are expanded, like users of Ant, Log4j, and Ivy
      are used to.

    + User configurable as to whether unresolved references like ${undefined}
      fail or are just left as literal '${undefined}' (without the quotes).

    + Type-checking.  Fast-fail if an attempt is made to overwrite a Project
      property with a value that is either null or a type other than String.
      (It is very useful to be able to overwrite Project String properties, so
      that is allowed).


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
        ...
        propFileLoader.load(file('build.properties')
        propFileLoader.load(file('local.properties')

Use plugin jar file locally.

    Just use your browser to go to the JavaPropFile directory at Maven
    Central.  http://repo1.maven.org/maven2/com/admc/gradle-javaPropFile-plugin
    Click into the version that you want.
    Right-click and download the only *.jar file in that directory.

    You can save the plugin jar with your project, thereby automatically
    sharing it with other project developers (assuming you use some SCM system).
    Or you can store it in a local directory, perhaps with other Gradle plugin
    jars.  The procedure is the same either way:


DETAILS

    Precedence works intuitively, not freakishly like Ant properties.
    The value of a variable will be the last value that was assigned to it.

    The provided method 'propFileLoader.traditionalPropertiesInit()'
    loads 'app.properties', prohibiting use of undefined ${...} references,
    then loads 'local.properties', allowing use of undefined references.

    Configurations:

        boolean propFileLoader.strict
            Whether ${...} references for unset properties are allowed
            (in which case the literal ${...} text is retained).
            Defaults to true

        boolean propFileLoader.expandSystemProperties
            Whether references to system properties, like ${user.home} are
            expanded.
            Defaults to true.
