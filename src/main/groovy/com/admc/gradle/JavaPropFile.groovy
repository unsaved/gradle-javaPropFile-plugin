package com.admc.gradle

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import java.util.regex.Matcher
import java.lang.reflect.Method
import java.lang.reflect.Constructor
import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.UnknownDomainObjectException

class JavaPropFile implements Action<Plugin> {
    private static final Pattern curlyRefGrpPattern =
            Pattern.compile(/\$\{([^}]+)\}/)
    private static final Pattern curlyRefPattern =
            Pattern.compile(/\$\{[^}]+\}/)
    private static final Pattern castFirstPattern =
            Pattern.compile(/\(([^)]+)\)(.+)/)
    private static final Pattern castLastPattern =
            Pattern.compile(/(.+)\(([^)]+)\)/)
    private static final Pattern castComponentsPattern =
            Pattern.compile(/([^\[]+)(?:\[(.+)\]([^\[\]]+)?)?/)

    private Project gp
    Behavior unsatisfiedRefBehavior = Behavior.THROW
    boolean overwrite = true
    boolean overwriteThrow
    boolean typeCasting
    String systemPropPrefix = 'sys|'
    private Map<String, Map<String, Object>> deferrals =
            new HashMap<String, Map<String, Object>>()
    private Map<String, Map<String, Object>> deferralDotDerefs =
            new HashMap<String, Map<String, Boolean>>()
    boolean defer = true
    private boolean callbackRegistered
    private List<String> orderedKeyList
    private Map<String, Boolean> dotDerefMap

    /**
     * Return a COPY of this internal structure
     */
    Map<String, String> getDeferredExtensionProps() {
        return new HashMap(deferrals)
    }

    // Giving up on UNSET because Gradle provides no way to remove a
    // property from a Project.  Project.properties.remove('x') does not work.
    enum Behavior { LITERAL, EMPTY, NO_SET, THROW }
    //enum Behavior { LITERAL, EMPTY, NO_SET, UNSET, THROW }

    JavaPropFile(Project p) {
        gp = p
    }

    /**
     * Wrapper for #load(File, String)
     */
    void load(File propFile) {
        load(propFile, null)
    }

    void load(File propFile, String defaultExtObjName) {
        assert unsatisfiedRefBehavior != null:
            '''unsatisfiedRefBehavior may not be set to null
'''
        assert propFile.isFile() && propFile.canRead():
            """Specified properties file inaccessible:  $propFile.absolutePath
"""
        assert systemPropPrefix == null || (systemPropPrefix.indexOf('.') < 0
                && systemPropPrefix.indexOf('$') < 0):
            """Specified systemPropPrefix conflicts with dereferencer or extension object
delimiter ('.' or '\$'): $systemPropPrefix
"""
        orderedKeyList = null
        dotDerefMap = null

        Set<String> handled = []  // Checking for conflicting assignments
        Properties propsIn = new Properties()
        propFile.withInputStream { propsIn.load(it) }
        Map<String, String> props =
                // Enumeration.toSet() not available until Groovy v. 1.8.0
                //propsIn.propertyNames().toSet().collectEntries {
                propsIn.propertyNames().toList().collectEntries {
            [(it): propsIn.getProperty(it).replace('\\$', '\u0004')]
        }
        assert props.size() == propsIn.size():
            ('Transformed ' + propsIn.size() + ' input properties into '
                    + props.size() + ' entries')
        gp.logger.info(
                'Loaded ' + props.size() + ' from ' + propFile.absolutePath)
        def duplicateDefs = [:]
        populateKeyLists(propFile)
        props.keySet().each {
            def count = orderedKeyList.count(it)
            if (count != 1) duplicateDefs[it] = count
        }
        if (duplicateDefs.size() > 0)
            throw new GradleException(
                    'Duplicate definitions present: ' + duplicateDefs)
        assert orderedKeyList.size() == dotDerefMap.size()
        String newValString
        boolean haveNewVal
        Matcher matcher
        int prevCount = props.size()
        List<String> unresolveds = []
        List<String> toRemove = []
        Pattern systemPropPattern = ((systemPropPrefix == null) 
                ? null
                : Pattern.compile('^\\Q' + systemPropPrefix + '\\E(.+)'))
        while (prevCount > 0) {
            unresolveds.clear()
            toRemove.clear()
            orderedKeyList.each { pk ->
                String pv = props.get(pk)
                Boolean dotDeref = dotDerefMap[pk]
                int dollarIndex
                haveNewVal = true
                newValString = pv.replaceAll(curlyRefGrpPattern) { matchGrps ->
                    // This block resolves ${references} in property values
                    if (systemPropPattern != null) {
                        matcher = systemPropPattern.matcher(matchGrps[1])
                        if (matcher.matches()) {
                            if (System.properties.containsKey(matcher.group(1)))
                                return System.properties[matcher.group(1)]
                            unresolveds << matchGrps[1]
                            haveNewVal = false
                            return matchGrps.first()
                        }
                    }
                    dollarIndex = matchGrps[1].indexOf('$')
                    if (dollarIndex > 0
                            && dollarIndex < matchGrps[1].length() - 1) {
                        String extObjName =
                                matchGrps[1].substring(0, dollarIndex)
                        String propName = matchGrps[1].substring(dollarIndex+1)
                        try {
                            return JavaPropFile.getNestedValue(dotDeref,
                                    gp.extensions.getByName(extObjName),
                                    propName)
                        } catch (UnknownDomainObjectException udoe) {
                            throw new GradleException(
                                "Domain Extension Object '$extObjName' "
                                + 'specified by reference ${' + matchGrps[1]
                                + '} is inaccessible', udoe)
                        } catch (MissingPropertyException mpe) {
                            throw new GradleException(
                                "No such property '$propName' available "
                                + "for Domain Extension Object '$extObjName'",
                                mpe)
                        }
                    } else if (JavaPropFile.hasNestedValue(
                            dotDeref, gp, matchGrps[1])) {
                        return JavaPropFile.getNestedValue(
                                dotDeref, gp, matchGrps[1]).toString()
                    }
                    unresolveds << matchGrps[1]
                    haveNewVal = false
                    return matchGrps.first()
                }
                if (haveNewVal) {
                    if (handled.contains(pk))
                        throw new GradleException(
                                "Conflicting assignments for property '$pk'")
                    assignOrDefer(dotDeref, defaultExtObjName,
                            pk, newValString, systemPropPattern)
                    handled << pk
                    toRemove << pk
                }
            }
            toRemove.each {
                props.remove(it)
                dotDerefMap.remove(it)
            }
            orderedKeyList -= toRemove
            if (prevCount == props.size()) break
            prevCount = props.size()
        }
        if (prevCount != 0) {
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
            orderedKeyList.each { pk ->
                String pv = props.get(pk)
                Boolean dotDeref = dotDerefMap[pk]
                switch (unsatisfiedRefBehavior) {
                  case Behavior.LITERAL:
                    assignOrDefer(dotDeref,
                            defaultExtObjName, pk, pv, systemPropPattern)
                    break
                  case Behavior.EMPTY:
                    assignOrDefer(dotDeref, defaultExtObjName, pk,
                            pv.replaceAll(curlyRefPattern, ''),
                            systemPropPattern)
                    break
                  /* See note above about Behavior.UNSET.
                   case Behavior.UNSET:
                     gp.properties.remove(pk)
                     break
                  */
                  default:
                    assert false:
                        "Unexpected Behavior value:  $unsatisfiedRefBehavior"
                }
            }
        }
        if (deferrals.size() > 0) {
            if (!callbackRegistered) {
                gp.plugins.whenPluginAdded(this)
                gp.logger.info(getClass().name
                        + ' registered for plugin addition notification')
                // How in hell do I cancel the callback???
            }
            gp.logger.info('Waiting for ' + deferrals.size()
                    + ' extension objects to appear as deferral targets')
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

    private static Object instantiateFromString(String str, String cStr) {
        assert str != null
        assert cStr != null
        if (cStr == '') return null
        Matcher matcher = castComponentsPattern.matcher(cStr)
        assert matcher.matches()
        String cName = matcher.group(1)
        String splitterStr = matcher.group(2)
        String colName = matcher.group(3)
        if (colName == null && splitterStr != null) colName = ''

        String[] strings = null
        if (splitterStr == null) {
            strings = [str]
        } else try {
            strings = str.split(splitterStr)
        } catch (PatternSyntaxException pse) {
            throw new GradleException(
                    "Malformatted splitter pattern:  $splitterStr", pse)
        }
        Collection colInstance = null;
        if (colName != null) {
            Class colClass
            if (colName == '') {
                colClass = ArrayList.class
            } else {
                colClass = JavaPropFile.resolveClass(colName)
            }
            assert colClass != null
            if (!Collection.class.isAssignableFrom(colClass))
                throw new GradleException('Specified type does not implement '
                        + "Collection: $colName")
            try {
                colInstance = colClass.newInstance()
            } catch (Exception e) {
                throw new GradleException(
                        'Failed to instantiate Collection instance of '
                        + "'$colName'", e)
            }
        }

        Class c = JavaPropFile.resolveClass(cName)
        Method m = null
        Constructor<?> cons = null
        try {
            m = c.getDeclaredMethod('valueOf', String.class)
        } catch (Exception e) {
            try {
                cons = c.getDeclaredConstructor(String.class)
            } catch (Exception nestedE) {
                throw new GradleException("TypeCasting class $cName has neither a static static .valueOf(String) method nor a (String) constructor", nestedE)
            }
        }
        assert (m == null && cons != null) || (m != null && cons == null)
        if (colName == null) try {
            return (m == null) ? cons.newInstance(str) : m.invoke(null, str)
        } catch (Exception e) {
            throw new GradleException("Failed to "
                    + ((m == null) ? 'construct' : 'valueOf')
                    + " a $c.name with param '$str'", e)
        }
        strings.each { try {
            colInstance <<
                    ((m == null) ? cons.newInstance(it) : m.invoke(null, it))
        } catch (Exception e) {
            throw new GradleException("Failed to "
                    + ((m == null) ? 'construct' : 'valueOf')
                    + " a $c.name with param '$it'", e)
        } }
        if (colName != '') return colInstance
        try {
            return colInstance.toArray(
                    java.lang.reflect.Array.newInstance(c, 0))
        } catch (Exception e) {
            throw new GradleException(
                    "Failed to convert internal List to array", e)
        }
    }

    /**
     * @param className A qualified class name, or an unqualified class name
     *                  that will be searched for according to Groovy default
     *                  package rules.
     * @return java.lang.Class instance.  Never returns null
     * @throws GradleException if specified class not accessible
     */
    public static Class resolveClass(String className) {
        if (className.indexOf('.') < 0) {
            for (pkg in JavaPropFile.defaultGroovyPackages) try {
                return Class.forName(pkg + '.' + className,
                        true, Thread.currentThread().contextClassLoader)
            } catch (Exception e) {
                // intentionally empty
            }
        } else try {
            return Class.forName(className,
                    true, Thread.currentThread().contextClassLoader)
        } catch (Exception e) {
            // intentionally empty
        }
        throw new GradleException("Requested class inaccessible:  $className")
    }

    /**
     * @return true if assignment made, false if assignment deferred
     */
    private boolean assignOrDefer(Boolean dotDeref, Object extObjName,
            String rawName, String rawValue, Pattern systemPropPattern) {
        boolean setSysProp = false
        Matcher matcher = null
        String cExpr = null
        String midName = null
        String valString = rawValue.replace('\u0004', '$')
        Object extensionObject = null

        String escapedName = (rawName.replace('\\$', '\u0001')
                .replace('\\(', '\u0002').replace('\\)', '\u0003'))
        if (systemPropPattern != null) {
            matcher = systemPropPattern.matcher(escapedName)
            setSysProp = matcher.matches()
        }
        if (setSysProp) {
            midName = matcher.group(1)
            extObjName = null   // sysProps have nothing to do with extObjs
        } else if (typeCasting) {
            if (escapedName.charAt(0) == '('
                    && escapedName.charAt(escapedName.length()-1) == ')')
                throw new GradleException(
                        "TypeCast name may not begin with ( and "
                        + "end with ): $escapedName")
            if (escapedName.length() > 2 && escapedName.startsWith("()")) {
                midName = escapedName.substring(2)
                if (valString.length() > 0)
                    throw new GradleException(
                            "Non-empty value supplied to a null "
                            + "typeCast for property '$midName'")
                cExpr = ''
            } else if (escapedName.length() > 2 && escapedName.endsWith("()")) {
                midName = escapedName.substring(0, escapedName.length() - 2)
                if (valString.length() > 0)
                    throw new GradleException(
                            "Non-empty value supplied to a null "
                            + "typeCast for property '$midName'")
                cExpr = ''
            } else {
                matcher = castFirstPattern.matcher(escapedName)
                if (matcher.matches()) {
                    cExpr = matcher.group(1)
                    midName = matcher.group(2)
                } else {
                    matcher = castLastPattern.matcher(escapedName)
                    if (matcher.matches()) {
                        midName = matcher.group(1)
                        cExpr = matcher.group(2)
                    } else {
                        midName = escapedName
                    }
                }
            }
        } else {
            midName = escapedName
        }
        assert midName != null
        if (!setSysProp) {
            int dollarIndex = midName.indexOf('$')
            if (dollarIndex > 0 && dollarIndex < midName.length() - 1) {
                // Override due to explicit ext Obj
                extObjName = (midName.substring(0, dollarIndex)
                        .replace('\u0001', '$')
                        .replace('\u0002', '(').replace('\u0003', ')'))
                midName = midName.substring(dollarIndex+1)
            }
            if (extObjName != null) try {
                extensionObject = gp.extensions.getByName(extObjName)
                extObjName = null  // Clear to prevent deferral
            } catch (UnknownDomainObjectException udoe) {
                if (!defer) throw new GradleException(
                        "Domain Extension Object '$extObjName' "
                        + "specified by property name '$rawName'"
                        + ' is inaccessible', udoe)
                // Non-null extObjName will be retained, triggering deferral
            }
        }
        String propName = (midName.replace('\u0001', '$')
                .replace('\u0002', '(').replace('\u0003', ')'))
        Object newVal = ((cExpr == null) ? valString
                : JavaPropFile.instantiateFromString(valString,
                cExpr.replace('\u0001', '$')
                .replace('\u0002', '(').replace('\u0003', ')')))
        if (extObjName == null) {
            assign(dotDeref, extensionObject, setSysProp, propName, newVal)
            return true
        }
        deferFor(dotDeref, extObjName,  propName, newVal)
        return false;
    }

    /**
     * Implementation of Action interface
     */
    void execute(Plugin plugin) {
        executeDeferrals()
    }

    void executeDeferrals() {
        if (deferrals.size() < 1) return
        Set<String> satisfieds = [] as Set
        Map<String, Boolean> ddrMap
        deferrals.each { objName, propMap ->
            Object extObj = null
            try {
                extObj = gp.extensions.getByName(objName)
            } catch (UnknownDomainObjectException udoe) {
                return
            }
            ddrMap = deferralDotDerefs[objName]
            propMap.each { name, value ->
                try {
                    setNestedValue(ddrMap[name], extObj, name, value)
                } catch (MissingPropertyException mpe) {
                    throw new GradleException(
                        "No such property '$name' available "
                        + "for Domain Extension Object '$objName'", mpe)
                }
            }
            satisfieds << objName
        }
        if (satisfieds.size() == 0) return
        satisfieds.each { deferrals.remove(it); deferralDotDerefs.remove(it) }
        if (deferrals.size() == 0)
            gp.logger.info(
                    'All JavaPropFile extension object deferrals satisfied')
        else
            gp.logger.info('Waiting for ' + deferrals.size()
                    + ' extension objects to appear as deferral targets')
    }

    private void deferFor(Boolean dotDeref,
            String extensionObjName, String propName, Object propVal) {
        if (!deferrals.containsKey(extensionObjName)) {
            deferrals[extensionObjName] = new HashMap<String, Object>()
            deferralDotDerefs[extensionObjName] = new HashMap<String, Boolean>()
        }
        deferrals[extensionObjName].put(propName, propVal)
        deferralDotDerefs[extensionObjName].put(propName, dotDeref)
        assert (deferrals[extensionObjName].keySet()
                == deferralDotDerefs[extensionObjName].keySet())
        assert deferralDotDerefs.keySet() == deferrals.keySet()
    }

    private assign(Boolean dotDeref,
            Object extObj, boolean isSys, String pName, Object val) {
        if (extObj != null
                || (isSys && System.properties.containsKey(pName))
                || (!isSys
                        && JavaPropFile.hasNestedValue(dotDeref, gp, pName))) {
            Object oldVal = null;
            if (isSys) {
                oldVal = System.properties[pName]
            } else if (extObj != null) try {
                oldVal = JavaPropFile.getNestedValue(dotDeref, extObj, pName)
            } catch (MissingPropertyException mpe) {
                throw new GradleException(
                        "No such property '$pName' available "
                        + 'for Domain Extension Object', mpe)
            } else {
                oldVal = JavaPropFile.getNestedValue(dotDeref, gp, pName)
            }
            // We will do absolutely nothing if either
            // (!overwrite && !overwriteThrow)
            // or if oldVar == newVar.
            if ((overwrite || overwriteThrow) && (oldVal != val
                    && ((oldVal == null || val == null)
                    || !oldVal.equals(val)))) {
                if (overwriteThrow)
                    throw new GradleException(
                            "Configured to prohibit property value "
                            + "changes, but attempted to change "
                            + "value of property '$pName' from "
                            + "'$oldVal' to '$val'")
                // Property value really changing
                if (oldVal != null && val != null
                        && !oldVal.class.equals(val.class))
                    throw new GradleException('Incompatible type '
                            + "for change of property '$pName'.  From "
                            + oldVal.class.name + ' to ' + val.class.name)
                writeValue(dotDeref, pName, val, isSys, extObj)
            }
        } else {
            // New property
            writeValue(dotDeref, pName, val, isSys, extObj)
        }
    }

    /**
     * Writes the value to Project property, Java property, or Extension object
     */
    private void writeValue(Boolean dotDeref, String name,
            Object value, boolean isSysProp, Object extensionObject) {
        try {
            if (isSysProp)
                System.setProperty(name, value)
            else if (extensionObject != null)
                JavaPropFile.setNestedValue(
                        dotDeref, extensionObject, name, value)
            else
                JavaPropFile.setNestedValue(dotDeref, gp, name, value)
        } catch (Exception e) {
            throw new GradleException(
                "Assignment to '$name' of value '$value' failed", e)
        }
    }

    /**
     * Returns sequence-preserving list of keys in a properties file.
     * This does not correspond exactly to java.util.Properties read from the
     * file, because this list preserves multiple elements for duplicate keys.
     */
    private void populateKeyLists(File f) {
        Properties workPs = new Properties();
        StringBuilder sb = new StringBuilder()
        orderedKeyList = []
        dotDerefMap = [:]
        String nextKey, pText
        List<String> tmpList
        int firstNonWs
        char firstDelim
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
            orderedKeyList << tmpList.first()
            firstNonWs = -1
            while (++firstNonWs < sb.length())
                if (!Character.isWhitespace(sb.charAt(firstNonWs))) break
            assert firstNonWs < sb.length()
            firstDelim = '\0'
            for (i in (firstNonWs + 1) .. sb.length()) {
                workPs.clear()
                workPs.load(new StringReader(sb.substring(firstNonWs, i)
                        + '\u0003\n'))
                if (workPs.size() == 1
                        && workPs.getProperty(workPs.propertyNames()
                        .toList().first()) == '\u0003') {
                        firstDelim = sb.charAt(i-1)
                    break
                }
            }
            if (firstDelim == '\0')
                throw new GradleException(
                    "Failed to determine property delimiter for record: $sb")
            dotDerefMap[tmpList.first()] = Character.isWhitespace(firstDelim)
            sb.length = 0
        }
    }

    /**
     * @throws MissingPropertyException if any element of propertyPath fails
     *         to resolve for the given extension object
     */
    private static void setNestedValue(Boolean dotDeref,
            Object topObject, String propertyPath, Object newValue) {
        if (!dotDeref) {
            topObject[propertyPath] = newValue
            return
        }
        List<String> tokens =
                propertyPath.replace('\\.', '\u001F').split('\\.', -1) as List
        String propertyName = tokens.pop()
        Object object = topObject
        tokens.each {
            object = object[it.replace('\u001F', '.')]
        }
        object[propertyName.replace('\u001F', '.')] = newValue
    }

    /**
     * Throws only from internal problems accessing the value
     */
    private static boolean hasNestedValue(
            Boolean dotDeref, Object topObject, String propertyPath) {
        try {
           JavaPropFile.getNestedValue(dotDeref, topObject, propertyPath)
        } catch (MissingPropertyException mpe) {
            return false
        }
        return true
    }


    /**
     * @throws MissingPropertyException if any element of propertyPath fails
     *         to resolve for the given extension object
     */
    private static Object getNestedValue(
            Boolean dotDeref, Object topObject, String propertyPath) {
        if (!dotDeref) return topObject[propertyPath]
        Object object = topObject
        propertyPath.replace('\\.', '\u001F').split('\\.', -1).each {
            object = object[it.replace('\u001F', '.')]
        }
        return object
    }
}
