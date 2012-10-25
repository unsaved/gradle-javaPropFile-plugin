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
import org.gradle.api.plugins.ExtraPropertiesExtension

class JavaPropFile implements Action<Plugin> {
    private static final Pattern curlyRefGrpPattern =
            Pattern.compile(/\$\{[-!\.]?([^}]+)\}/)
    private static final Pattern curlyRefGrpPatternDflt =
            Pattern.compile(/\$\{([^-!\.}][^}]*)\}/)
    private static final Pattern curlyRefGrpPatternDot =
            Pattern.compile(/\$\{\.([^}]+)\}/)
    private static final Pattern curlyRefGrpPatternBang =
            Pattern.compile(/\$\{\!([^}]+)\}/)
    private static final Pattern curlyRefGrpPatternHyphen =
            Pattern.compile(/\$\{-([^}]+)\}/)
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
    private String keyPrefix
    private Map<String, Map<String, Object>> deferrals =
            new HashMap<String, Map<String, Object>>()
    private Map<String, Map<String, Object>> deferralDotDerefs =
            new HashMap<String, Map<String, Boolean>>()
    boolean defer = true
    private boolean callbackRegistered
    private List<String> orderedKeyList
    private Map<String, Boolean> dotDerefMap
    private Map<String, Object> targMap
    private String dfltExtObjName

    /**
     * Return a COPY of this internal structure
     */
    Map<String, Map<String, Object>> getDeferredExtensionProps() {
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
     * Load a properties file into Gradle Project
     *
     * Wrapper for #generalLoad(File, String, String, Map)
     */
    void load(File propFile) {
        generalLoad(propFile, null, null, null)
    }

    /**
     * Load a properties file into Gradle Project, using specified property
     * name prefix for each loaded property.
     *
     * Wrapper for #generalLoad(File, String, String, Map)
     */
    void load(File propFile, String keyAssignPrefix) {
        assert keyAssignPrefix != null:
            '''Value of keyAssignPrefix must be non-null to disambiguate parameter types.
Use the 1-parameter load method if your intention is to assign no key prefix.
'''
        generalLoad(propFile, keyAssignPrefix, null, null)
    }

    /**
     * Load a properties file into Map.  If you want to instantiate a new map,
     * just specify <CODE>[:]</CODE> as the map parameter value.
     *
     * Wrapper for #generalLoad(File, String, String, Map)
     */
    Map load(File propFile, Map targetMap) {
        assert targetMap != null:
            '''Value of targetMap must be non-null to disambiguate parameter types.
Use the 1-parameter load method if your intention is to assign no key prefix.
'''
        generalLoad(propFile, null, null, targetMap)
    }

    /**
     * Load a properties file into Map, using specified property
     * name prefix for each loaded property.
     If you want to instantiate a new map,
     * just specify <CODE>[:]</CODE> as the map parameter value.
     *
     * Wrapper for #generalLoad(File, String, String, Map)
     */
    Map load(File propFile, String keyAssignPrefix, Map targetMap) {
        assert targetMap != null:
            '''Value of targetMap must be non-null to disambiguate parameter types.
Use the appropriate 2-parameter load method if you don't want to set targetMap.
'''
        generalLoad(propFile, keyAssignPrefix, null, targetMap)
    }

    /**
     * Load a properties file into an extension object.
     *
     * Wrapper for #generalLoad(File, String, String)
     */
    void loadIntoExtensionObject(File propFile, String defaultExtObjName) {
        load(propFile, null, defaultExtObjName)
    }

    /**
     * Load a properties file into an extension object.
     *
     * Wrapper for #generalLoad(File, String, String, Map)
     *
     * @defaultExtObjName  This applies only to which properties are SET.
     *                     It has no effect on property references.
     *                     If you want to reference an extension object, then
     *                     you must explicity use the {extObjName$..} prefix
     *                     syntax.
     */
    void load(File propFile, String keyAssignPrefix, String defaultExtObjName) {
        assert defaultExtObjName != null:
            '''Value of defaultExtObjName must be non-null to disambiguate parameter types.
Use the appropriate load method if your intention is to assign no
defaultExtObjName.
'''
        generalLoad(propFile, null, defaultExtObjName, null)
    }

    synchronized private Map generalLoad(File propFile, String keyAssignPrefix,
            String defaultExtObjName, Map targetMap) {
        assert unsatisfiedRefBehavior != null:
            '''unsatisfiedRefBehavior may not be set to null
'''
        assert propFile != null:
            """'propFile' parameter may not be null
"""

        assert propFile.isFile() && propFile.canRead():
            """Specified properties file inaccessible:  $propFile.absolutePath
"""
        assert systemPropPrefix == null || (systemPropPrefix.indexOf('.') < 0
                && systemPropPrefix.indexOf('$') < 0):
            """Specified systemPropPrefix conflicts with dereferencer or extension object
delimiter ('.' or '\$'): $systemPropPrefix
"""
        targMap = targetMap
        dfltExtObjName = defaultExtObjName
        assert targMap == null || dfltExtObjName == null:
        '''Settings 'targetMap and 'defaultExtObjName' are mutually exclusive'''
        keyPrefix = keyAssignPrefix

        orderedKeyList = null
        dotDerefMap = null

        Set<String> handled = []  // Checking for conflicting assignments
        Properties propsIn = new Properties()
        propFile.withInputStream { propsIn.load(it) }
        Map<String, String> props =
                propsIn.propertyNames().toSet().collectEntries {
            [(it) : propsIn.getProperty(it).replace('\\$', '\u0004')
                    .replace('\\}', '\u0005')]
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
        String newValString, mg0, mg1, mg1de
        boolean satisfied
        Matcher matcher
        int prevCount = props.size()
        List<String> toRemove = []
        Pattern systemPropPattern = ((systemPropPrefix == null) 
                ? null
                : Pattern.compile('^\\Q' + systemPropPrefix + '\\E(.+)'))
        while (prevCount > 0) {
            toRemove.clear()
            orderedKeyList.each { pk ->
                String pv = props.get(pk)
                Boolean dotDeref = dotDerefMap[pk]
                int dollarIndex
                satisfied = true
                newValString = pv.replaceAll(curlyRefGrpPattern) { matchGrps ->
                    // This block resolves ${references} in property values
                    mg0 = matchGrps.first()
                    mg1 = matchGrps[1].replace('\u0005', '}')
                    if (mg1.charAt(0) == '\\' && mg1.length() > 1)
                        mg1 = mg1.substring(1)
                    mg1de = mg1.replace('\u0004', '$') // dollar-escaped
                    if (systemPropPattern != null) {
                        matcher = systemPropPattern.matcher(mg1de)
                        if (matcher.matches()) {
                            if (System.properties.containsKey(matcher.group(1)))
                                return System.properties[matcher.group(1)]
                            satisfied = false
                            return mg0
                        }
                    }
                    dollarIndex = mg1.indexOf('$')
                    if (dollarIndex > 0 && dollarIndex < mg1.length() - 1) {
                        String extObjName = mg1.substring(0, dollarIndex)
                        String propName = mg1.substring(dollarIndex+1)
                        try {
                            return getPossiblyNestedValue(dotDeref,
                                    gp.extensions.getByName(extObjName),
                                    propName)
                        } catch (UnknownDomainObjectException udoe) {
                            throw new GradleException(
                                "Domain Extension Object '$extObjName' "
                                + 'specified by reference ${' + mg1
                                + '} is inaccessible', udoe)
                        } catch (MissingPropertyException mpe) {
                            throw new GradleException(
                                "No such property '$propName' available "
                                + "for Domain Extension Object '$extObjName'",
                                mpe)
                        }
                    }
                    if (hasPossiblyNestedValue(dotDeref, gp, mg1de))
                        return getPossiblyNestedValue(
                                dotDeref, gp, mg1de).toString()
                    satisfied = false
                    return mg0
                }
                if (satisfied) {
                    if (handled.contains(pk))
                        throw new GradleException(
                                "Conflicting assignments for property '$pk'")
                    assignOrDefer(dotDeref, pk, newValString, systemPropPattern)
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
            Set throwKeys = []
            Set throwRefs = []
            Set zeroKeys = []
            Set zeroRefs = []
            Set literalKeys = []
            Set literalRefs = []
            Set nosetKeys = []
            Set nosetRefs = []

            orderedKeyList.each { pk ->
                // Each unsatisfied property definition
                String pv = props.get(pk)
                Boolean dotDeref = dotDerefMap[pk]
                int dollarIndex
                boolean doNotSet = false

                // 1:  Handle unresolved ${ref} values
                newValString = pv.replaceAll(curlyRefGrpPatternDflt) {
                        matchGrps ->
                    mg0 = matchGrps.first()
                    mg1 = matchGrps[1].replace('\u0005', '}')
                    if (mg1.charAt(0) == '\\' && mg1.length() > 1)
                        mg1 = mg1.substring(1)
                    mg1de = mg1.replace('\u0004', '$') // dollar-escaped
                    if (systemPropPattern != null) {
                        matcher = systemPropPattern.matcher(mg1de)
                        if (matcher.matches()) {
                            if (System.properties.containsKey(matcher.group(1)))
                                return System.properties[matcher.group(1)]
                            switch (unsatisfiedRefBehavior) {
                              // case Behavior.UNSET:  See note above re. UNSET
                              case Behavior.LITERAL:
                                literalKeys << pk
                                literalRefs << matcher.group(1)
                                return mg0
                              case Behavior.EMPTY:
                                zeroKeys << pk
                                zeroRefs << matcher.group(1)
                                return ''
                              case Behavior.NO_SET:
                                nosetKeys << pk
                                nosetRefs << matcher.group(1)
                                doNotSet = true
                                return '*'  // Dummy return val
                              case Behavior.THROW:
                                throwKeys << pk
                                throwRefs << matcher.group(1)
                                return '*'  // Dummy return val
                              default:
                                assert false:
                                    "Unexpected Behavior value:  $unsatisfiedRefBehavior"
                            }
                        }
                    }
                    dollarIndex = mg1.indexOf('$')
                    if (dollarIndex > 0
                            && dollarIndex < mg1.length() - 1) {
                        String extObjName = mg1.substring(0, dollarIndex)
                        String propName = mg1.substring(dollarIndex+1)
                        try {
                            return getPossiblyNestedValue(dotDeref,
                                    gp.extensions.getByName(extObjName),
                                    propName)
                        } catch (Exception e) {
                            assert false: '''
Failed to resolve DomainExtensionObject ref though succeeded earlier:
\'$''' + extObjName + '\' reference ${' + mg1 + '}: ' + e
                        }
                    }
                    if (hasPossiblyNestedValue(dotDeref, gp, mg1de))
                        return getPossiblyNestedValue(
                                dotDeref, gp, mg1de).toString()
                    switch (unsatisfiedRefBehavior) {
                      // case Behavior.UNSET:  See note above re. UNSET
                      case Behavior.LITERAL:
                        literalKeys << pk
                        literalRefs << mg1de
                        return mg0
                      case Behavior.EMPTY:
                        zeroKeys << pk
                        zeroRefs << mg1de
                        return ''
                      case Behavior.NO_SET:
                        nosetKeys << pk
                        nosetRefs << mg1de
                        doNotSet = true
                        return '*'  // Dummy return val
                      case Behavior.THROW:
                        throwKeys << pk
                        throwRefs << mg1de
                        return '*'  // Dummy return val
                      default:
                        assert false:
                            "Unexpected Behavior value:  $unsatisfiedRefBehavior"
                    }
                }
                // 2:  Handle unresolved ${!ref} values
                .replaceAll(curlyRefGrpPatternBang) {
                        matchGrps ->
                    mg1 = mg1
                    mg1de = mg1.replace('\u0004', '$') // dollar-escaped
                    if (systemPropPattern != null) {
                        matcher = systemPropPattern.matcher(mg1de)
                        if (matcher.matches()) {
                            if (System.properties.containsKey(matcher.group(1)))
                                return System.properties[matcher.group(1)]
                            throwKeys << pk
                            throwRefs << matcher.group(1)
                            return '*'  // Dummy return val
                        }
                    }
                    dollarIndex = mg1.indexOf('$')
                    if (dollarIndex > 0 && dollarIndex < mg1.length() - 1) {
                        String extObjName = mg1.substring(0, dollarIndex)
                        String propName = mg1.substring(dollarIndex+1)
                        try {
                            return getPossiblyNestedValue(dotDeref,
                                    gp.extensions.getByName(extObjName),
                                    propName)
                        } catch (Exception e) {
                            assert false: '''
Failed to resolve DomainExtensionObject ref though succeeded earlier:
\'$''' + extObjName + '\' reference ${' + mg1 + '}: ' + e
                        }
                    }
                    if (hasPossiblyNestedValue(dotDeref, gp, mg1de))
                        return getPossiblyNestedValue(
                                dotDeref, gp, mg1de).toString()
                    throwKeys << pk
                    throwRefs << mg1de
                    return '*'  // Dummy return val
                }
                // 3:  Handle unresolved ${-ref} values
                .replaceAll(curlyRefGrpPatternHyphen) {
                        matchGrps ->
                    mg1 = matchGrps[1]
                    mg1de = mg1.replace('\u0004', '$') // dollar-escaped
                    if (systemPropPattern != null) {
                        matcher = systemPropPattern.matcher(mg1de)
                        if (matcher.matches()) {
                            if (System.properties.containsKey(matcher.group(1)))
                                return System.properties[matcher.group(1)]
                            zeroKeys << pk
                            zeroRefs << matcher.group(1)
                            return ''
                        }
                    }
                    dollarIndex = mg1.indexOf('$')
                    if (dollarIndex > 0 && dollarIndex < mg1.length() - 1) {
                        String extObjName = mg1.substring(0, dollarIndex)
                        String propName = mg1.substring(dollarIndex+1)
                        try {
                            return getPossiblyNestedValue(dotDeref,
                                    gp.extensions.getByName(extObjName),
                                    propName)
                        } catch (Exception e) {
                            assert false: '''
Failed to resolve DomainExtensionObject ref though succeeded earlier:
\'$''' + extObjName + '\' reference ${' + mg1 + '}: ' + e
                        }
                    }
                    if (hasPossiblyNestedValue(dotDeref, gp, mg1de))
                        return getPossiblyNestedValue(
                                dotDeref, gp, mg1de).toString()
                    zeroKeys << pk
                    zeroRefs << mg1de
                    return ''
                }
                // 4:  Handle unresolved ${.ref} values
                .replaceAll(curlyRefGrpPatternDot) {
                        matchGrps ->
                    mg1 = matchGrps[1]
                    mg0 = matchGrps.first()
                    mg1de = mg1.replace('\u0004', '$') // dollar-escaped
                    if (systemPropPattern != null) {
                        matcher = systemPropPattern.matcher(mg1de)
                        if (matcher.matches()) {
                            if (System.properties.containsKey(matcher.group(1)))
                                return System.properties[matcher.group(1)]
                            literalKeys << pk
                            literalRefs << matcher.group(1)
                            return mg0
                        }
                    }
                    dollarIndex = mg1.indexOf('$')
                    if (dollarIndex > 0 && dollarIndex < mg1.length() - 1) {
                        String extObjName = mg1.substring(0, dollarIndex)
                        String propName = mg1.substring(dollarIndex+1)
                        try {
                            return getPossiblyNestedValue(dotDeref,
                                    gp.extensions.getByName(extObjName),
                                    propName)
                        } catch (Exception e) {
                            assert false: '''
Failed to resolve DomainExtensionObject ref though succeeded earlier:
\'$''' + extObjName + '\' reference ${' + mg1 + '}: ' + e
                        }
                    }
                    if (hasPossiblyNestedValue(dotDeref, gp, mg1de))
                        return getPossiblyNestedValue(
                                dotDeref, gp, mg1de).toString()
                    literalKeys << pk
                    literalRefs << mg1de
                    return mg0
                }

                if (!doNotSet)
                    assignOrDefer(dotDeref, pk, newValString, systemPropPattern)
            }

            if (throwKeys.size() > 0)
                throw new GradleException(
                    'Unable to resolve top-level properties: ' + throwKeys
                    + '\ndue to unresolved references to: ' + throwRefs)
            if (zeroKeys.size() > 0)
                gp.logger.warn(
                        'Top-level properties include empty-strings: ' + zeroKeys
                        + '\ndue to unresolved references to: ' + zeroRefs)
            if (literalKeys.size() > 0)
                gp.logger.warn(
                        'Top-level properties include non-expanded refs: ' + literalKeys
                        + '\ndue to unresolved references to: ' + literalRefs)
            if (nosetKeys.size() > 0)
                gp.logger.warn(
                        'Top-level properties not set: ' + nosetKeys
                        + '\ndue to unresolved references to: ' + nosetRefs)
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
        return targMap
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
        Collection colInstance = null
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
                throw new GradleException("TypeCasting class $cName has neither a static .valueOf(String) method nor a (String) constructor", nestedE)
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
     * Context classloader lookup, applying Groovy's default package rules.
     *
     * @param className A qualified class name, or an unqualified class name
     *                  that will be searched for according to Groovy default
     *                  package rules.
     * @return java.lang.Class instance.  Never returns null
     * @throws GradleException if specified class not accessible
     */
    static Class resolveClass(String className) {
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
    private boolean assignOrDefer(Boolean dotDeref,
            String rawName, String rawValue, Pattern systemPropPattern) {
        String extObjName = dfltExtObjName
        boolean setSysProp = false
        Matcher matcher = null
        String cExpr = null
        String midName = null
        String valString = rawValue.replace('\u0004', '$')
        Object extensionObject = null

        // System property assignments not honored if a Map is targeted
        if (targMap != null) {
            systemPropPattern = null
            extObjName = null
        }

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
        // extensionObject assignments not supported if target is a Map or a
        // System property.
        if (targMap == null && !setSysProp) {
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
        return false
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
                    setPossiblyNestedValue(ddrMap[name], extObj, name, value)
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
        assert targMap == null:
            'deferFor method invoked even though targetMap is assigned'
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
                || (!isSys && hasPossiblyNestedValue(
                        targMap == null && dotDeref, gp,
                        (keyPrefix == null) ? pName : (keyPrefix + pName)))) {
            Object oldVal = null
            if (isSys) {
                oldVal = System.properties[pName]
            } else if (extObj != null) try {
                oldVal = getPossiblyNestedValue(dotDeref, extObj,
                    (keyPrefix == null) ? pName : (keyPrefix + pName))
            } catch (MissingPropertyException mpe) {
                throw new GradleException(
                        "No such property '$pName' available "
                        + 'for Domain Extension Object', mpe)
            } else {
                oldVal = getPossiblyNestedValue(targMap == null && dotDeref,
                        gp, (keyPrefix == null) ? pName : (keyPrefix + pName))
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
     * Writes the value to Project property, Java system property, target Map,
     * or Extension object
     */
    private void writeValue(Boolean dotDeref, String name,
            Object value, boolean isSysProp, Object extensionObject) {
        if (keyPrefix != null) name = keyPrefix + name
        try {
            if (targMap != null)
                JavaPropFile.setPossiblyNestedValue(
                        false, targMap, name, value)
            else if (isSysProp)
                System.setProperty(name, value)
            else if (extensionObject != null)
                JavaPropFile.setPossiblyNestedValue(
                        dotDeref, extensionObject, name, value)
            else
                JavaPropFile.setPossiblyNestedValue(dotDeref, gp, name, value)
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
        Properties workPs = new Properties()
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
            try {
                workPs.load(new StringReader(pText))
            } catch (MissingMethodException mme) {
                // Hack is workaround for pre-1.6 Java
                workPs.load(new StringBufferInputStream(pText))
            }
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
                try {
                    workPs.load(new StringReader(sb.substring(firstNonWs, i)
                            + '\u0003\n'))
                } catch (MissingMethodException mme) {
                    // Hack is workaround for pre-1.6 Java
                    workPs.load(new StringBufferInputStream(sb.substring(firstNonWs, i)
                            + '\u0003\n'))
                }
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
    private static void setPossiblyNestedValue(Boolean dotDeref,
            Object topObject, String propertyPath, Object newValue) {
        /* For the two tests beginning with 'topObject.hasProperty' and
         * 'object.hasProperty', see
         * http://forums.gradle.org/gradle/topics/project_description_vs_project_ext_description
         */
        if (!dotDeref) {
            if (topObject.hasProperty('ext') && ExtraPropertiesExtension.
              isAssignableFrom(topObject.ext.getClass()) &&
              !topObject.hasProperty(propertyPath))
                topObject.ext[propertyPath] = newValue
            else
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
        if (object.hasProperty('ext') &&
          ExtraPropertiesExtension.isAssignableFrom(object.ext.getClass()) &&
          !object.hasProperty(propertyName))
            object.ext[propertyName.replace('\u001F', '.')] = newValue
        else
            object[propertyName.replace('\u001F', '.')] = newValue
    }

    /**
     * If ALL of these conditions are true then the specified Project topObject
     * is ignored and the targMap will be read instead.<UL>
     *  <LI>Instance variable targMap is non-null
     *  <LI>Specified topObject is the gp of this instance
     *  <LI>param dotDeref is false
     * </UL>
     * Therefore, you must dot-deref project references when a targMap is
     * used, and must do \\.-escaping to prevent that.
     * 
     * @throws RuntimeException only from internal problems accessing the value
     */
    private boolean hasPossiblyNestedValue(
            Boolean dotDeref, Object topObject, String propertyPath) {
        try {
           getPossiblyNestedValue(dotDeref, topObject, propertyPath)
        } catch (MissingPropertyException mpe) {
            return false
        }
        return true
    }

    /**
     * If ALL of these conditions are true then the specified Project topObject
     * is ignored and the targMap will be read instead.<UL>
     *  <LI>Instance variable targMap is non-null
     *  <LI>Specified topObject is the gp of this instance
     *  <LI>param dotDeref is false
     * </UL>
     * Therefore, you must dot-deref project references when a targMap is
     * used, and must do \\.-escaping to prevent that.
     * 
     * @throws MissingPropertyException if any element of propertyPath fails
     *         to resolve for the given extension object
     */
    private Object getPossiblyNestedValue(
            Boolean dotDeref, Object topObject, String propertyPath) {
        if (!dotDeref) {
            if (targMap == null || topObject != gp) {
                if ((topObject instanceof Map)
                        && !topObject.containsKey(propertyPath))
                    throw new MissingPropertyException(
                        "Map has no property key '$propertyPath'")
                return topObject[propertyPath]
            }
            if (!targMap.containsKey(propertyPath))
                throw new MissingPropertyException(
                    "Specified map has no property key '$propertyPath'")
            return targMap[propertyPath]
        }
        String key
        Object object = topObject
        propertyPath.replace('\\.', '\u001F').split('\\.', -1).each {
            key = it.replace('\u001F', '.')
            if ((object instanceof Map) && !object.containsKey(key))
                throw new MissingPropertyException(
                    "Map has no property key '$key'")
            object = object[key]
        }
        return object
    }

    /**
     * Wrapper for #expand(File, Map, boolean), with dotDeref false.
     */
    String expand(File inFile, Map<String, Object> sourceMap) {
        return expand(inFile, sourceMap, false)
    }

    /**
     * Wrapper for #expand(File, Map, boolean), with no sourceMap so that
     * the Gradle Project is checked for properties by default.
     */
    String expand(File inFile, boolean dotDeref) {
        return expand(inFile, null, dotDeref)
    }

    /**
     * Wrapper for #expand(File, Map, boolean), with dotDeref false and
     * no sourceMap so that the Gradle Project is checked for properties by
     * default.
     */
    String expand(File inFile) {
        return expand(inFile, null, false)
    }

    /**
     * Just like .expand(String, Map, boolean), but uses specified text file
     * content, with the environment's default encoding, as the input String.

     * @see #expand(String, Map, boolean)
     */
    String expand(
            File inFile, Map<String, Object> sourceMap, boolean dotDeref) {
        if (!inFile.canRead())
            throw new GradleException(
                    "Input file not readable: $inFile.absolutePath")
        if (!inFile.isFile())
            throw new GradleException("Not a file: $inFile.absolutePath")
        return expand(inFile.text, sourceMap, dotDeref)
    }

    /**
     * Wrapper for #expand(File, Map, boolean, String), with dotDeref false.
     */
    String expand(
            File inFile, Map<String, Object> sourceMap, String encoding) {
        return expand(inFile, sourceMap, false, encoding)
    }

    /**
     * Wrapper for #expand(File, Map, boolean, String),
     * with no sourceMap so that
     * the Gradle Project is checked for properties by default.
     */
    String expand(File inFile, boolean dotDeref, String encoding) {
        return expand(inFile, null, dotDeref, encoding)
    }

    /**
     * Wrapper for #expand(File, Map, boolean, String), with dotDeref false and
     * no sourceMap so that the Gradle Project is checked for properties by
     * default.
     */
    String expand(File inFile, String encoding) {
        return expand(inFile, null, false, encoding)
    }

    /**
     * Just like .expand(String, Map, boolean), but uses specified text file
     * content, with the specified encoding, as the input String.

     * @see #expand(String, Map, boolean)
     */
    String expand(File inFile,
            Map<String, Object> sourceMap, boolean dotDeref, String encoding) {
        if (!inFile.canRead())
            throw new GradleException(
                    "Input file not readable: $inFile.absolutePath")
        if (!inFile.isFile())
            throw new GradleException("Not a file: $inFile.absolutePath")
        return expand(inFile.getText(encoding), sourceMap, dotDeref)
    }

    /**
     * Wrapper for #expand(String, Map, boolean), with dotDeref false.
     */
    String expand(String inString, Map<String, Object> sourceMap) {
        return expand(inString, sourceMap, false)
    }

    /**
     * Wrapper for #expand(String, Map, boolean), with no sourceMap so that
     * the Gradle Project is checked for properties by default.
     */
    String expand(String inString, boolean dotDeref) {
        return expand(inString, null, dotDeref)
    }

    /**
     * Wrapper for #expand(String, Map, boolean), with dotDeref false and
     * no sourceMap so that the Gradle Project is checked for properties by
     * default.
     */
    String expand(String inString) {
        return expand(inString, null, false)
    }

    /**
     * Since Beavior.NO_SET makes no sense when we are expanding and not
     * setting anything, this method treats (rather arbitrarily but
     * definitely) behavior.NO_SET exactly the same as if Behavior.LITERAL
     * was actually set.
     */
    synchronized public String expand(
            String inString, Map<String, Object> sourceMap, boolean dotDeref) {
        /* Synchronized because instance field 'targMap' is referenced by
         * called instance methods. */
        Set throwRefs = []
        Set zeroRefs = []
        Set literalRefs = []
        Matcher matcher
        String newValString, mg0, mg1, mg1de
        targMap = null  // We do no assignments
        Pattern systemPropPattern = ((systemPropPrefix == null) 
                ? null
                : Pattern.compile('^\\Q' + systemPropPrefix + '\\E(.+)'))

        int dollarIndex

        // 1:  Handle ${ref} values
        newValString = inString.replaceAll(curlyRefGrpPatternDflt) {
                matchGrps ->
            mg0 = matchGrps.first()
            mg1 = matchGrps[1].replace('\u0005', '}')
            if (mg1.charAt(0) == '\\' && mg1.length() > 1)
                mg1 = mg1.substring(1)
            mg1de = mg1.replace('\u0004', '$') // dollar-escaped
            if (systemPropPattern != null) {
                matcher = systemPropPattern.matcher(mg1de)
                if (matcher.matches()) {
                    if (System.properties.containsKey(matcher.group(1)))
                        return System.properties[matcher.group(1)]
                    switch (unsatisfiedRefBehavior) {
                      // case Behavior.UNSET:  See note above re. UNSET
                      // See class JavaDoc above about Behavior.NO_SET here.
                      case Behavior.LITERAL:
                      case Behavior.NO_SET:
                        literalRefs << matcher.group(1)
                        return mg0
                      case Behavior.EMPTY:
                        zeroRefs << matcher.group(1)
                        return ''
                      case Behavior.THROW:
                        throwRefs << matcher.group(1)
                        return '*'  // Dummy return val
                      default:
                        assert false:
                            "Unexpected Behavior value:  $unsatisfiedRefBehavior"
                    }
                }
            }
            dollarIndex = mg1.indexOf('$')
            if (dollarIndex > 0
                    && dollarIndex < mg1.length() - 1) {
                String extObjName = mg1.substring(0, dollarIndex)
                String propName = mg1.substring(dollarIndex+1)
                try {
                    return getPossiblyNestedValue(dotDeref,
                            gp.extensions.getByName(extObjName),
                            propName)
                } catch (Exception e) {
                    assert false: '''
Failed to resolve DomainExtensionObject ref though succeeded earlier:
\'$''' + extObjName + '\' reference ${' + mg1 + '}: ' + e
                }
            }
            if (hasPossiblyNestedValue(dotDeref,
                    ((sourceMap == null) ? gp : sourceMap), mg1de))
                return getPossiblyNestedValue(dotDeref,
                        ((sourceMap == null) ? gp : sourceMap), mg1de)
                        .toString()
            switch (unsatisfiedRefBehavior) {
              // case Behavior.UNSET:  See note above re. UNSET
              case Behavior.LITERAL:
              case Behavior.NO_SET:
                literalRefs << mg1de
                return mg0
              case Behavior.EMPTY:
                zeroRefs << mg1de
                return ''
              case Behavior.THROW:
                throwRefs << mg1de
                return '*'  // Dummy return val
              default:
                assert false:
                    "Unexpected Behavior value:  $unsatisfiedRefBehavior"
            }
        }
        // 2:  Handle ${!ref} values
        .replaceAll(curlyRefGrpPatternBang) {
                matchGrps ->
            mg1 = matchGrps[1]
            mg1de = mg1.replace('\u0004', '$') // dollar-escaped
            if (systemPropPattern != null) {
                matcher = systemPropPattern.matcher(mg1de)
                if (matcher.matches()) {
                    if (System.properties.containsKey(matcher.group(1)))
                        return System.properties[matcher.group(1)]
                    throwRefs << matcher.group(1)
                    return '*'  // Dummy return val
                }
            }
            dollarIndex = mg1.indexOf('$')
            if (dollarIndex > 0 && dollarIndex < mg1.length() - 1) {
                String extObjName = mg1.substring(0, dollarIndex)
                String propName = mg1.substring(dollarIndex+1)
                try {
                    return getPossiblyNestedValue(dotDeref,
                            gp.extensions.getByName(extObjName),
                            propName)
                } catch (Exception e) {
                    assert false: '''
Failed to resolve DomainExtensionObject ref though succeeded earlier:
\'$''' + extObjName + '\' reference ${' + mg1 + '}: ' + e
                }
            }
            if (hasPossiblyNestedValue(dotDeref,
                    ((sourceMap == null) ? gp : sourceMap), mg1de))
                return getPossiblyNestedValue(dotDeref,
                        ((sourceMap == null) ? gp : sourceMap), mg1de)
                        .toString()
            throwRefs << mg1de
            return '*'  // Dummy return val
        }
        // 3:  Handle ${-ref} values
        .replaceAll(curlyRefGrpPatternHyphen) {
                matchGrps ->
            mg1 = matchGrps[1]
            mg1de = mg1.replace('\u0004', '$') // dollar-escaped
            if (systemPropPattern != null) {
                matcher = systemPropPattern.matcher(mg1de)
                if (matcher.matches()) {
                    if (System.properties.containsKey(matcher.group(1)))
                        return System.properties[matcher.group(1)]
                    zeroRefs << matcher.group(1)
                    return ''
                }
            }
            dollarIndex = mg1.indexOf('$')
            if (dollarIndex > 0 && dollarIndex < mg1.length() - 1) {
                String extObjName = mg1.substring(0, dollarIndex)
                String propName = mg1.substring(dollarIndex+1)
                try {
                    return getPossiblyNestedValue(dotDeref,
                            gp.extensions.getByName(extObjName),
                            propName)
                } catch (Exception e) { assert false: '''
Failed to resolve DomainExtensionObject ref though succeeded earlier:
\'$''' + extObjName + '\' reference ${' + mg1 + '}: ' + e
                }
            }
            if (hasPossiblyNestedValue(dotDeref,
                    ((sourceMap == null) ? gp : sourceMap), mg1de))
                return getPossiblyNestedValue(dotDeref,
                        ((sourceMap == null) ? gp : sourceMap), mg1de)
                        .toString()
            zeroRefs << mg1de
            return ''
        }
        // 4:  Handle ${.ref} values
        .replaceAll(curlyRefGrpPatternDot) {
                matchGrps ->
            mg1 = matchGrps[1]
            mg0 = matchGrps.first()
            mg1de = mg1.replace('\u0004', '$') // dollar-escaped
            if (systemPropPattern != null) {
                matcher = systemPropPattern.matcher(mg1de)
                if (matcher.matches()) {
                    if (System.properties.containsKey(matcher.group(1)))
                        return System.properties[matcher.group(1)]
                    literalRefs << matcher.group(1)
                    return mg0
                }
            }
            dollarIndex = mg1.indexOf('$')
            if (dollarIndex > 0 && dollarIndex < mg1.length() - 1) {
                String extObjName = mg1.substring(0, dollarIndex)
                String propName = mg1.substring(dollarIndex+1)
                try {
                    return getPossiblyNestedValue(dotDeref,
                            gp.extensions.getByName(extObjName),
                            propName)
                } catch (Exception e) {
                    assert false: '''
Failed to resolve DomainExtensionObject ref though succeeded earlier: \'$''' + extObjName + '\' reference ${' + mg1 + '}: ' + e
                }
            }
            if (hasPossiblyNestedValue(dotDeref,
                    ((sourceMap == null) ? gp : sourceMap), mg1de))
                return getPossiblyNestedValue(dotDeref,
                        ((sourceMap == null) ? gp : sourceMap), mg1de)
                        .toString()
            literalRefs << mg1de
            return mg0
        }

        if (throwRefs.size() > 0)
            throw new GradleException(
                'Unable to resolve references: ' + throwRefs)
        if (zeroRefs.size() > 0)
            gp.logger.warn(
                    'Zero\'d unresolved references: ' + zeroRefs)
        if (literalRefs.size() > 0)
            gp.logger.warn(
                    'Did not expand unresolved references: ' + literalRefs)

        return newValString
    }
}
