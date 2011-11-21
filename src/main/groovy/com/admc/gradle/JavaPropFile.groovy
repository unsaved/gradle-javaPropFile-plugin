package com.admc.gradle

import java.util.regex.Pattern
import java.util.regex.Matcher
import java.lang.reflect.Method
import java.lang.reflect.Constructor
import org.gradle.api.Project
import org.gradle.api.GradleException

class JavaPropFile {
    private static final Pattern curlyRefGrpPattern =
            Pattern.compile(/\$\{([^}]+)\}/)
    private static final Pattern curlyRefPattern =
            Pattern.compile(/\$\{[^}]+\}/)
    private static final Pattern castFirstPattern =
            Pattern.compile(/\(([^)]+)\)(.+)/)
    private static final Pattern castLastPattern =
            Pattern.compile(/(.+)\(([^)]+)\)/)

    private Project gp
    Behavior unsatisfiedRefBehavior = Behavior.THROW
    boolean expandSystemProps = true
    boolean overwrite = true
    boolean overwriteThrow
    boolean typeCasting
    String systemPropPrefix

    // Giving up on UNSET because Gradle provides no way to remove a
    // property from a Project.  Project.properties.remove('x') does not work.
    enum Behavior { LITERAL, EMPTY, NO_SET, THROW }
    //enum Behavior { LITERAL, EMPTY, NO_SET, UNSET, THROW }

    JavaPropFile(Project p) {
        gp = p
    }

    void load(File propFile) {
        assert unsatisfiedRefBehavior != null:
            '''unsatisfiedRefBehavior may not be set to null'''
        assert propFile.isFile() && propFile.canRead():
            """Specified properties file inaccessible:  $propFile.absolutePath
"""
        List<String> orderedKeyList = orderedKeyList(propFile)
        Properties propsIn = new Properties()
        propFile.withInputStream { propsIn.load(it) }
        Map<String, String> props =
                // Enumeration.toSet() not available until Groovy v. 1.8.0
                //propsIn.propertyNames().toSet().collectEntries {
                propsIn.propertyNames().toList().collectEntries {
//System.out.println("From ($it) to ...")
//System.out.println(propsIn.getProperty(it))
            [(it): propsIn.getProperty(it).replace('\\$', '\u0004')]
        }
        assert props.size() == propsIn.size():
            ('Transformed ' + propsIn.size() + ' input properties into '
                    + props.size() + ' entries')
        gp.logger.info(
                'Loaded ' + props.size() + ' from ' + propFile.absolutePath)
        String newValString
        boolean haveNewVal
        int prevCount = props.size()
        def unresolveds = []
        Pattern systemPropPattern = ((systemPropPrefix == null) 
                ? null
                : Pattern.compile("^\\Q" + systemPropPrefix + "\\E(.+)"))
        while (prevCount > 0) {
            unresolveds.clear()
            new HashMap(props).each() { pk, pv ->
                haveNewVal = true
                newValString = pv.replaceAll(curlyRefGrpPattern) { matchGrps ->
                    // This block resolves ${references} in property values
                    if (gp.hasProperty(matchGrps[1])
                            && (gp.property(matchGrps[1]) instanceof String))
                        return gp.property(matchGrps[1])
                    if (expandSystemProps
                            && System.properties.containsKey(matchGrps[1]))
                        return System.properties[matchGrps[1]]
                    unresolveds << matchGrps[1]
                    haveNewVal = false
                    return matchGrps[0]
                }
                if (haveNewVal) {
                    assign(pk, newValString, systemPropPattern)
                    props.remove(pk)
                }
            }
            if (prevCount == props.size()) break
            prevCount = props.size()
        }
        if (prevCount == 0) return
        // If we get here, then we have unsatisfied references
        if (unsatisfiedRefBehavior == Behavior.THROW)
            throw new GradleException(
                'Unable to resolve top-level properties: ' + props.keySet()
                + '\ndue to unresolved references to: ' + unresolveds)
        gp.logger.warn(
                'Unable to resolve top-level properties: ' + props.keySet()
                + '\ndue to unresolved references to: ' + unresolveds
                + '.\nWill handle according to unsatisifiedRefBehavior: '
                + unsatisfiedRefBehavior)
        if (unsatisfiedRefBehavior == Behavior.NO_SET) return
        new HashMap(props).each() { pk, pv ->
            switch (unsatisfiedRefBehavior) {
              case Behavior.LITERAL:
                assign(pk, pv, systemPropPattern)
                break
              case Behavior.EMPTY:
                assign(pk,
                        pv.replaceAll(curlyRefPattern, ''), systemPropPattern)
                break
              /* See not above about Behavior.UNSET.
              case Behavior.UNSET:
                gp.properties.remove(pk)
                break
             */
              default:
                assert false: "Unexpected Behavior value:  $unsatisfiedRefBehavior"
            }
        }
    }

    void traditionalPropertiesInit() {
        Behavior originalBehavior = unsatisfiedRefBehavior
        boolean originalOverwrite = overwrite
        boolean originalOverwriteThrow = overwriteThrow

        File appPropertiesFile = gp.file('app.properties')
        File localPropertiesFile = gp.file('local.properties')
        // Unlike Ant, the LAST loaded properties will override.
        unsatisfiedRefBehavior = Behavior.THROW
        overwrite = true
        overwriteThrow = false
        if (appPropertiesFile.isFile()) load(appPropertiesFile)

        unsatisfiedRefBehavior = Behavior.NO_SET
        if (localPropertiesFile.isFile()) load(localPropertiesFile)

        unsatisfiedRefBehavior = originalBehavior
        overwrite = originalOverwrite
        overwriteThrow = originalOverwriteThrow
    }

    private static final List defaultGroovyPackages =
            ['java.lang', 'java.io', 'java.math', 'java.net', 'java.util']
        // java.math is a slight over-simplification because Groovy only
        // includes BigDecimal and BigInteger from that package, but there
        // are only 2 other things in the package.

    private static Object instantiateFromString(String str, String cName) {
        assert str != null
        assert cName != null
        if (cName == '') return null
        Class<?> c = null;
        if (cName.indexOf('.') < 0) {
            for (pkg in JavaPropFile.defaultGroovyPackages) try {
                c = Class.forName(pkg + '.' + cName)
                break
            } catch (Exception e) {
                // intentionally empty
            }
        } else try {
            c = Class.forName(cName)
        } catch (Exception e) {
            // intentionally empty
        }
        if (c == null)
            throw new GradleException("Inaccessible typeCasting class: $cName")
        Method m = null
        try {
            m = c.getDeclaredMethod("valueOf", String.class)
        } catch (Exception e) {
            // Intentionally empty
        }
        if (m != null) try {
            return m.invoke(null, str)
        } catch (Exception e) {
            throw new GradleException("Failed to generate a $c.name with param '$str': $e")
        }
        Constructor<?> cons = null
        try {
            cons = c.getDeclaredConstructor(String.class)
        } catch (Exception e) {
            throw new GradleException("TypeCasting class $cName has neither a static static .valueOf(String) method nor a (String) constructor")
        }
        try {
            return cons.newInstance(str)
        } catch (Exception e) {
            throw new GradleException("Failed to construct a $c.name with param '$str': $e")
        }
    }

    private void assign(
            String rawName, String rawString, Pattern systemPropPattern) {
        boolean setSysProp = false
        Matcher matcher = null
        String cName = null
        String propName = null
        String valString = rawString.replace('\u0004', '$')

        if (systemPropPattern != null) {
            matcher = systemPropPattern.matcher(rawName)
            setSysProp = matcher.matches()
        }
        if (setSysProp) {
            propName = matcher.group(1)
        } else if (typeCasting) {
            if (rawName.charAt(0) == '('
                    && rawName.charAt(rawName.length()-1) == ')')
                throw new GradleException(
                        "TypeCast name may not begin with ( and "
                        + "end with ): $rawName")
            if (rawName.length() > 2 && rawName.startsWith("()")) {
                propName = rawName.substring(2)
                if (valString.length() > 0)
                    throw new GradleException(
                            "Non-empty value supplied to a null "
                            + "typeCast for property '$propName'")
                cName = ''
            } else if (rawName.length() > 2 && rawName.endsWith("()")) {
                propName = rawName.substring(0, rawName.length() - 2)
                if (valString.length() > 0)
                    throw new GradleException(
                            "Non-empty value supplied to a null "
                            + "typeCast for property '$propName'")
                cName = ''
            } else {
                matcher = castFirstPattern.matcher(rawName)
                if (matcher.matches()) {
                    cName = matcher.group(1)
                    propName = matcher.group(2)
                } else {
                    matcher = castLastPattern.matcher(rawName)
                    if (matcher.matches()) {
                        propName = matcher.group(1)
                        cName = matcher.group(2)
                    } else {
                        propName = rawName
                    }
                }
            }
        } else {
            propName = rawName
        }
        assert propName != null
        Object newVal = ((cName == null) ? valString
                : JavaPropFile.instantiateFromString(valString, cName))
        if ((setSysProp && System.properties.containsKey(propName))
                || (!setSysProp && gp.hasProperty(propName))) {
            Object oldVal = (setSysProp ? System.properties[propName]
                    : gp.property(propName))
            // We will do absolutely nothing if either
            // (!overwrite && !overwriteThrow)
            // or if oldVar == newVar.
            if ((overwrite || overwriteThrow) && (oldVal != newVal
                    && ((oldVal == null || newVal == null)
                    || !oldVal.equals(newVal)))) {
                if (overwriteThrow)
                    throw new GradleException(
                            "Configured to prohibit property value "
                            + "changes, but attempted to change "
                            + "value of property '$propName' from "
                            + "'$oldVal' to '$newVal'")
                // Property value really changing
                if (oldVal != null && newVal != null
                        && !oldVal.class.equals(newVal.class))
                    throw new GradleException("Incompatible type "
                            + "for change of property "
                            + "'$propName'.  From "
                            + oldVal.class.name + ' to '
                            + newVal.class.name)
                if (setSysProp)
                    System.setProperty(propName, newVal)
                else
                    gp.setProperty(propName, newVal)
            }
        } else {
            // New property
            if (setSysProp)
                System.setProperty(propName, newVal)
            else
                gp.setProperty(propName, newVal)
        }
    }

    List<String> orderedKeyList(File f) {
        Properties workPs = new Properties();
        StringBuilder sb = new StringBuilder()
        List<String> keyList = []
        String nextKey, pText
        List<String> tmpList
        f.readLines('ISO-8859-1').each {
            sb.append(it).append('\n')
            pText = sb + '\u0003=\n'
            workPs.clear()
            workPs.load(new StringReader(pText))
            tmpList = workPs.propertyNames().toList()
            if (tmpList.size() < 2) return
            assert tmpList.size() == 2:
                ('Parsing error.  ' + workPs.size()
                        + " properties from:  $pText\n$tmpList")
            assert workPs.getProperty('\u0003') != null:
                ('Parsing error.  Got 2 properties but not EOT from:  '
                        + "$pText\n$tmpList")
            tmpList.remove('\u0003')
            keyList << tmpList[0]
            sb.length = 0
        }
        return keyList
    }
}
