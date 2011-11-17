package com.admc.gradle

import java.util.regex.Pattern
import org.gradle.api.Project
import org.gradle.api.GradleException

class JavaPropFile {
    private static final Pattern curlyRefGrpPattern =
            Pattern.compile(/\$\{([^}]+)\}/)
    private static final Pattern curlyRefPattern =
            Pattern.compile(/\$\{[^}]+\}/)

    private Project gp
    Behavior unsatisfiedRefBehavior = Behavior.THROW
    boolean expandSystemProperties = true
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
        Properties props = new Properties()
        propFile.withInputStream { props.load(it) }
        gp.logger.info(
                'Loaded ' + props.size() + ' from ' + propFile.absolutePath)
        String newVal
        boolean haveNewVal
        int prevCount = props.size()
        def unresolveds = []
        while (prevCount > 0) {
            unresolveds.clear()
            new HashMap(props).each() { pk, pv ->
                haveNewVal = true
                newVal = pv.replaceAll(curlyRefGrpPattern) { matchGrps ->
                    // This block resolves ${references} in property values
                    if (gp.hasProperty(matchGrps[1])
                            && (gp.property(matchGrps[1]) instanceof String))
                        return gp.property(matchGrps[1])
                    if (expandSystemProperties
                            && System.properties.containsKey(matchGrps[1]))
                        return System.properties[matchGrps[1]]
                    unresolveds << matchGrps[1]
                    haveNewVal = false
                    return matchGrps[0]
                }
                if (haveNewVal) {
                    if (gp.hasProperty(pk)) {
                        if (!gp.property(pk).equals(newVal)) {
                            if (gp.property(pk) == null)
                                throw new GradleException(
                                "Property setting '$pk' attempts to override null property value")
                            if (!(gp.property(pk) instanceof String))
                                throw new GradleException(
                                "Property setting '$pk' attempts to override non-String property value: ${gp.property(pk).class.name}")
                            gp.setProperty(pk, newVal)
                        }
                    } else {
                        gp.setProperty(pk, newVal)
                    }
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
                gp.setProperty(pk, pv)
                break
              case Behavior.EMPTY:
                gp.setProperty(pk, pv.replaceAll(curlyRefPattern, ''))
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
        File appPropertiesFile = gp.file('app.properties')
        File localPropertiesFile = gp.file('local.properties')
        // Unlike Ant, the LAST loaded properties will override.
        unsatisfiedRefBehavior = Behavior.THROW
        if (appPropertiesFile.exists()) load(appPropertiesFile)
        unsatisfiedRefBehavior = Behavior.NO_SET
        if (localPropertiesFile.exists()) load(localPropertiesFile)
        unsatisfiedRefBehavior = originalBehavior
    }
}
