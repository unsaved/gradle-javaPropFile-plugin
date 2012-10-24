package com.admc.gradle

import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import static org.junit.Assert.*

class JavaPropFilePluginTest {
    /* Test authors:  Remember when testing \-escapes using File.write()s for
        properties files, that you are writing text to be interpretedy by
        java.util.Porperties, and you must therefore double up an extra time on
        \ characters, like:
        f.write('alpha=one\\\\${escaped}two')
     */
    private Project project

    {
        project = ProjectBuilder.builder().build()
        project.apply plugin: JavaPropFilePlugin
    }

    private static File mkTestFile() {
        File newFile = File.createTempFile(getClass().simpleName, '.properties')
        newFile.deleteOnExit()
        return newFile
    }

    private void checkProps(String... cProps) {
        cProps.each {
            assert !project.hasProperty(it):
                "Gradle project has property '$it' set before test begins"
            System.clearProperty(it)
        }
    }

    @org.junit.Test
    void trivialPropertySet() {
        checkProps('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=one', 'ISO-8859-1')
        project.propFileLoader.load(f)

        assertTrue(project.hasProperty('alpha'))
        assertEquals('one', project.property('alpha'))
    }

    @org.junit.Test
    void prefixedPropertySet() {
        checkProps('pref.alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=one', 'ISO-8859-1')
        project.propFileLoader.load(f, 'pref.')

        assertTrue(project.hasProperty('pref.alpha'))
        assertEquals('one', project.property('pref.alpha'))
    }

    @org.junit.Test(expected=GradleException.class)
    void prefProhibitOverwrite() {
        project.ext.set('pref.alpha', 'eins')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=one', 'ISO-8859-1')
        project.propFileLoader.load(f, 'pref.')
    }

    @org.junit.Test
    void prefOverwrite() {
        project.ext.set('pref.alpha', 'eins')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=one', 'ISO-8859-1')
        project.propFileLoader.load(f, 'pref.')

        assertTrue(project.hasProperty('pref.alpha'))
        assertEquals('one', project.property('pref.alpha'))
    }

    @org.junit.Test
    void prefNestRef() {
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${pref.beta}post\nbeta=one', 'ISO-8859-1')
        project.propFileLoader.load(f, 'pref.')

        assertTrue(project.hasProperty('pref.alpha'))
        assertTrue(project.hasProperty('pref.beta'))
        assertEquals('preonepost', project.property('pref.alpha'))
        assertEquals('one', project.property('pref.beta'))
    }

    @org.junit.Test
    void sysProperty() {
        checkProps('me')

        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.systemPropPrefix = 'sp|'
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('me=I am ${sp|user.name}', 'ISO-8859-1')
        project.propFileLoader.load(f)

        assertTrue(project.hasProperty('me'))
        assertEquals( 'I am ' + System.properties['user.name'],
                project.property('me'))
    }

    @org.junit.Test
    void refNest() {
        checkProps('alpha', 'beta')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=one${beta}\nbeta=two', 'ISO-8859-1')
        project.propFileLoader.load(f)

        assertTrue(project.hasProperty('alpha'))
        assertTrue(project.hasProperty('beta'))
        assertEquals('two', project.property('beta'))
        assertEquals('onetwo', project.property('alpha'))
    }

    @org.junit.Test(expected=GradleException.class)
    void typeCollision() {
        checkProps('aFile')

        project.ext.aFile = new File('x.txt')
        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('aFile=one', 'ISO-8859-1')
        project.propFileLoader.load(f)
    }

    @org.junit.Test
    void changeToNull() {
        checkProps('aNull')
        project.ext.set('aNull', (String) null)

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('aNull=one', 'ISO-8859-1')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('aNull'))
        assertEquals('one', project.property('aNull'))
    }

    @org.junit.Test(expected=GradleException.class)
    void unsetLoneThrow() {
        checkProps('notset', 'alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=${notset}', 'ISO-8859-1')
        project.propFileLoader.load(f)
    }

    @org.junit.Test(expected=GradleException.class)
    void unsetSandwichedThrow() {
        checkProps('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${notset}post', 'ISO-8859-1')
        project.propFileLoader.load(f)
    }

    @org.junit.Test
    void unsetLoneNoSet() {
        checkProps('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=${notset}', 'ISO-8859-1')
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.NO_SET
        project.propFileLoader.load(f)
        assertFalse(project.hasProperty('alpha'))
        project.ext.set('alpha', 'eins')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('eins', project.property('alpha'))
    }

    @org.junit.Test
    void unsetSandwichedNoSet() {
        checkProps('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${notset}post', 'ISO-8859-1')
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.NO_SET
        project.propFileLoader.load(f)
        assertFalse(project.hasProperty('alpha'))
        project.ext.set('alpha', 'eins')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('eins', project.property('alpha'))
    }

    /*  See comment about Behavior.UNSET in JavaPropFile.java.
    @org.junit.Test
    void unsetLoneUnSet() {
        checkProps('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=${notset}', 'ISO-8859-1')
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.UNSET
        project.propFileLoader.load(f)
        assertFalse(project.hasProperty('alpha'))
        project.ext.set('alpha', 'eins')
        project.propFileLoader.load(f)
        assertFalse(project.hasProperty('alpha'))
    }

    @org.junit.Test
    void unsetSandwichedUnSet() {
        checkProps('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${notset}post', 'ISO-8859-1')
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.UNSET
        project.propFileLoader.load(f)
        assertFalse(project.hasProperty('alpha'))
        project.ext.set('alpha', 'eins')
        project.propFileLoader.load(f)
        assertFalse(project.hasProperty('alpha'))
    }
    */

    @org.junit.Test
    void unsetLoneLiteral() {
        checkProps('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=${notset}', 'ISO-8859-1')
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.LITERAL
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('${notset}', project.property('alpha'))
    }

    @org.junit.Test
    void unsetSandwichedLiteral() {
        checkProps('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${notset}post', 'ISO-8859-1')
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.LITERAL
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('pre${notset}post', project.property('alpha'))
    }

    @org.junit.Test
    void unsetLoneEmpties() {
        checkProps('alpha', 'beta')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=${notset}\nsp|beta=pre${alsoNotSet}post', 'ISO-8859-1')
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.EMPTY
        project.propFileLoader.systemPropPrefix = 'sp|'
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertFalse(project.hasProperty('beta'))
        assertFalse(System.properties.containsKey('alpha'))
        assertTrue(System.properties.containsKey('beta'))
        assertEquals('', project.property('alpha'))
        assertEquals('prepost', System.properties['beta'])
    }

    @org.junit.Test
    void unsetSandwichedEmpty() {
        checkProps('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${notset}post', 'ISO-8859-1')
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.EMPTY
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('prepost', project.property('alpha'))
    }

    @org.junit.Test
    void deepRefNest() {
        checkProps('bottom1', 'mid2', 'mid3a', 'mid3b', 'top4')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('''
# White space in settings is to test that it is tolerated and ignored
mid3a=m3a ${bottom1} ${mid2}
top4=t4 ${mid3a} ${mid3b} ${bottom1}
    mid3b   m3b ${mid2}
bottom1  =  Bottom
mid2   m2 ${bottom1}
''', 'ISO-8859-1')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('top4'))
        assertEquals('t4 m3a Bottom m2 Bottom m3b m2 Bottom Bottom',
                project.property('top4'))
    }

    @org.junit.Test
    void overwrite() {
        checkProps('alpha', 'beta')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=replacement\nbeta=${beta} addition')
        project.ext.set('alpha', 'eins')
        project.ext.set('beta', 'zwei')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertTrue(project.hasProperty('beta'))
        assertEquals('replacement', project.property('alpha'))
        assertEquals('zwei addition', project.property('beta'))
    }

    @org.junit.Test
    void noOverwrite() {
        checkProps('alpha', 'beta')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=replacement\nbeta=${beta} addition')
        project.ext.set('alpha', 'eins')
        project.ext.set('beta', 'zwei')
        project.propFileLoader.overwrite = false
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertTrue(project.hasProperty('beta'))
        assertEquals('eins', project.property('alpha'))
        assertEquals('zwei', project.property('beta'))
    }

    @org.junit.Test
    void setSysProps() {
        checkProps('alpha', 'sys|file.separator', 'sys|alpha')
        assert !project.hasProperty('file.separator'):
            '''Project has property 'file.separator' set before we start test'''
        
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('sys|alpha=eins\nsys|file.separator=*')
        project.propFileLoader.load(f)
        assertFalse(project.hasProperty('alpha'))
        assertFalse(project.hasProperty('file.separator'))
        assertEquals('*', System.properties['file.separator'])
        assertEquals('eins', System.properties['alpha'])
    }

    @org.junit.Test
    void traditionalSanityCheck() {
        // Can't test much specifically, but we know that the method can at
        // least be called.
        project.propFileLoader.traditionalPropertiesInit()
    }

    @org.junit.Test(expected=GradleException.class)
    void overwriteThrow() {
        checkProps('alpha')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=zwei', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.ext.set('alpha', 'eins')
        project.propFileLoader.load(f)
    }

    @org.junit.Test
    void nochangeOverwrite() {
        checkProps('alpha')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=eins', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.ext.set('alpha', 'eins')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('eins', project.property('alpha'))
    }

    @org.junit.Test
    void nullAssignments() {
        checkProps('alpha', 'beta', 'gamma', 'delta')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('''
alpha=
# There is trailing whitespace on next two lines:
beta()=  
  gamma()  =  
delta()=
''', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        ['alpha', 'beta', 'gamma', 'delta'].each {
            assertTrue("Missing property '$it'", project.hasProperty(it))
        }
        assertEquals('', project.property('alpha'))
        ['beta', 'gamma', 'delta'].each {
            assertNull("Non-null property '$it'", project.property(it))
        }
    }

    @org.junit.Test
    void castedNochangeOverwrite() {
        checkProps('alpha', 'beta', 'gamma', 'delta')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha(File)=eins\n(File)beta=zwei\ngamma()=', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        project.ext.set('alpha', new File('eins'))
        project.ext.set('beta', new File('zwei'))
        project.ext.set('gamma', null)
        project.ext.set('delta', '')
        project.propFileLoader.load(f)
        ['alpha', 'beta', 'gamma', 'delta'].each {
            assertTrue("Missing property '$it'", project.hasProperty(it))
        }
        assertEquals(new File('eins'), project.property('alpha'))
        assertEquals(new File('zwei'), project.property('beta'))
        assertNull(project.property('gamma'))
        assertEquals('', project.property('delta'))
    }

    @org.junit.Test
    void typeCasting() {
        checkProps('aFile', 'aLong')

        File f = JavaPropFilePluginTest.mkTestFile()
        // One using String cons. + one using valueOf methods
        f.write('aFile(File)=eins\n(Long)aLong=9764', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('aFile'))
        assertTrue(project.hasProperty('aLong'))
        assertEquals(new File('eins'), project.property('aFile'))
        assertEquals(Long.valueOf(9764L), project.property('aLong'))
    }

    @org.junit.Test
    void nonCastingParens() {
        checkProps(
                'alpha(File)x', 'x(File)beta', 'alpha', 'beta',
                'alpha()x', 's()alpha')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha(File)x=eins\nx(File)beta=zwei', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        ['alpha', 'beta'].each {
            assertFalse("Missing property '$it'", project.hasProperty(it))
        }
        ['alpha(File)x', 'x(File)beta'].each {
            assertTrue("Present property '$it'", project.hasProperty(it))
        }
        assertEquals('eins', project.property('alpha(File)x'))
        assertEquals('zwei', project.property('x(File)beta'))
    }

    @org.junit.Test(expected=GradleException.class)
    void overCasted1() {
        checkProps('epsilon()', '(File)epsilon()')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('(File)epsilon()=sechs', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
    }

    @org.junit.Test(expected=GradleException.class)
    void overCasted2() {
        checkProps('(File)delta(junk)')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('(File)delta(junk)=vier', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
    }

    @org.junit.Test(expected=GradleException.class)
    void overCasted3() {
        checkProps('gamma', '(gamma)')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('(File)delta(junk)=vier', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
    }

    @org.junit.Test(expected=GradleException.class)
    void emptyCast() {
        checkProps('()')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('()=', 'ISO-8859-1')
        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
    }

    @org.junit.Test(expected=GradleException.class)
    void malformattedNull() {
        checkProps('alpha')

        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha()=x', 'ISO-8859-1')
        project.propFileLoader.load(f)
    }

    @org.junit.Test(expected=GradleException.class)
    void conflictingAssignments() {
        checkProps('alpha', 'beta')
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=1\nalpha=2', 'ISO-8859-1')
        project.propFileLoader.load(f)
    }

    @org.junit.Test(expected=GradleException.class)
    void missingCastingClass() {
        checkProps('alpha')

        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha(NoSuchClass)=x', 'ISO-8859-1')
        project.propFileLoader.load(f)
    }

    @org.junit.Test
    void parenthesizedSysProps() {
        checkProps(
                '(File)systemProp|alpha', 'beta(File)', 'gamma()', 'alpha',
                'beta', 'gamma', 'systemProp|alpha', 'systemProp|beta(File)',
                'systemProp|gamma()')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('''
(File)systemProp|alpha=eins
systemProp|beta(File)=zwei
systemProp|gamma()=
''')
        project.propFileLoader.systemPropPrefix = 'systemProp|'
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        ['alpha', 'beta', 'gamma', 'systemProp|alpha', '(File)systemProp|alpha',
                'systemProp|beta(File)', 'systemProp|gamma()'].each {
            assertFalse("System property '$it' is set",
                    System.properties.containsKey(it))
                
        }
        ['alpha', 'beta', 'gamma', 'beta(File)', 'gamma()',
                'systemProp|beta(File)', 'systemProp|gamma()'].each {
            assertFalse("Project has property '$it'", project.hasProperty(it))
        }

        assertTrue(project.hasProperty('systemProp|alpha'))
        assertTrue(System.properties.containsKey('beta(File)'))
        assertTrue(System.properties.containsKey('gamma()'))
        assertEquals(new File('eins'), project.property('systemProp|alpha'))
        assertEquals('zwei', System.properties['beta(File)'])
        assertEquals('', System.properties['gamma()'])
    }

    @org.junit.Test
    void escapeRef() {
        checkProps('alpha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=one\\\\${escaped}two')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('one${escaped}two', project.property('alpha'))
    }

    @org.junit.Test
    void escapedDotRef() {
        checkProps('al.pha', 'beta')

        project.ext.set('al.pha', 'one')
        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('beta =pre${al\\\\.pha}post')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('al.pha'))
        assertTrue(project.hasProperty('beta'))
        assertEquals('preonepost', project.property('beta'))
    }

    @org.junit.Test
    void escapeNameDollar() {
        checkProps('al$pha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('al\\\\$pha=one')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('al$pha'))
        assertEquals('one', project.property('al$pha'))
    }

    @org.junit.Test
    void escapedDotSet() {
        checkProps('al.pha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('al\\\\.pha =one')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('al.pha'))
        assertEquals('one', project.property('al.pha'))
    }

    @org.junit.Test
    void escapeNameOpenParen() {
        checkProps('(al)pha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('\\\\(al)pha=one')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('(al)pha'))
        assertEquals('one', project.property('(al)pha'))
    }

    @org.junit.Test
    void escapeNameCloseParen() {
        checkProps('(al)pha')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('(al\\\\)pha=one')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('(al)pha'))
        assertEquals('one', project.property('(al)pha'))
    }

    @org.junit.Test(expected=GradleException.class)
    void noDefer() {
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('mockBean$str2=val')
        project.propFileLoader.defer = false
        project.propFileLoader.load(f)
    }

    @org.junit.Test
    void deferredExtObjAssignment() {
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('mockBean$str2=val')
        project.propFileLoader.load(f)
        assertEquals(1, project.propFileLoader.deferredExtensionProps.size())
        project.apply plugin: MockPlugin
        //assertNull(project.mockBean.str2)
        // If executeDeferrals not invoked via callback, do:
        //project.propFileLoader.executeDeferrals()
        assertEquals('val', project.mockBean.str2)
        assertEquals(0, project.propFileLoader.deferredExtensionProps.size())
    }

    @org.junit.Test
    void deferredExtObjNestAssignment() {
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('''
mockBean$tHolder2(com.admc.gradle.MockBean$ThreadHolder) =New Thread Name
mockBean$tHolder2.heldThread.name =Renamed Thread
        ''')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertEquals(1, project.propFileLoader.deferredExtensionProps.size())
        project.apply plugin: MockPlugin
        assertEquals(0, project.propFileLoader.deferredExtensionProps.size())
        //assertEquals('name:New Thread Name',
        assertEquals('Renamed Thread', project.mockBean.tHolder2.heldThread.name)
    }

    @org.junit.Test
    void extObjAssignment() {
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('mockBean$str2=val')
        project.apply plugin: MockPlugin
        assertNull(project.mockBean.str2)
        project.propFileLoader.load(f)
        assertEquals('val', project.mockBean.str2)
    }

    @org.junit.Test
    void extObjRef() {
        checkProps('alpha')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${mockBean$str1}post')
        project.apply plugin: MockPlugin
        project.mockBean.assignSome()
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('preonepost', project.property('alpha'))
    }

    @org.junit.Test
    void objNestRef() {
        checkProps('alpha')
        project.propFileLoader.overwriteThrow = true

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${mockBean$str1}post')
        project.apply plugin: MockPlugin
        project.mockBean.assignSome()
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('preonepost', project.property('alpha'))
    }

    @org.junit.Test
    void castColObjNestRef() {
        checkProps('alpha')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${mockBean$strList}post')
        project.apply plugin: MockPlugin
        project.mockBean.assignSome()
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('pre' + ['ONE', 'TWO', 'THREE'].toString() + 'post',
                project.property('alpha'))
    }

    @org.junit.Test
    void ObjDeepNestRef() {
        checkProps('alpha')
        project.propFileLoader.overwriteThrow = true

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha =pre${mockBean$tHolder1.heldThread.name}post')
        project.apply plugin: MockPlugin
        project.mockBean.assignSome()
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertEquals('prename:unopost', project.property('alpha'))
    }


    @org.junit.Test
    void castColObjNestSet() {
        project.propFileLoader.typeCasting = true

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('mockBean$intList(Integer[\\\\|]ArrayList)=91|72|101')
        project.apply plugin: MockPlugin
        project.propFileLoader.load(f)
        assertEquals([91, 72, 101], project.mockBean.intList)
    }

    @org.junit.Test
    void objDeepNestSet() {
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('mockBean$tHolder1.heldThread.name =New Thread Name')
        project.apply plugin: MockPlugin
        project.mockBean.assignSome()
        project.propFileLoader.load(f)
        assertEquals('New Thread Name',
                project.mockBean.tHolder1.heldThread.name)
    }

    @org.junit.Test
    void arrayCast() {
        checkProps('alpha')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha(Integer[\\ ])=94 3 12')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertNotNull(project.hasProperty('alpha'))
        assertEquals('[Ljava.lang.Integer;',
                project.property('alpha').class.name)
        assertArrayEquals((Integer[]) [94, 3, 12], project.property('alpha'))
    }

    @org.junit.Test
    void listCast() {
        checkProps('alpha')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha(Integer[\\ ]ArrayList)=94 3 12')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertNotNull(project.hasProperty('alpha'))
        assertEquals(ArrayList.class, project.property('alpha').class)
        assertEquals([94, 3, 12] as ArrayList, project.property('alpha'))
    }

    @org.junit.Test
    void setCast() {
        checkProps('alpha')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha(Integer[\\ ]HashSet)=94 3 12')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertNotNull(project.hasProperty('alpha'))
        assertEquals(HashSet.class, project.property('alpha').class)
        assertEquals([94, 3, 12] as HashSet, project.property('alpha'))
    }

    @org.junit.Test
    void modifyExistingSeeChange() {
        checkProps('alpha', 'beta', 'gamma')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('beta=two${alpha}\nalpha=eins\ngamma=three${alpha}\n')
        project.ext.set('alpha', 'one')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertTrue(project.hasProperty('beta'))
        assertTrue(project.hasProperty('gamma'))
        assertEquals('eins', project.property('alpha'))
        assertEquals('twoone', project.property('beta'))
        assertEquals('threeeins', project.property('gamma'))
    }

    @org.junit.Test
    void modifyExistingNoSeeChange() {
        checkProps('alpha', 'beta')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=eins\nbeta=two${alpha}\n')
        project.ext.set('alpha', 'one')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('alpha'))
        assertTrue(project.hasProperty('beta'))
        assertEquals('eins', project.property('alpha'))
        assertEquals('twoeins', project.property('beta'))
    }

    @org.junit.Test
    void projCastObjNesting() {
        checkProps('t1')
        project.propFileLoader.typeCasting = true

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('t1(Thread)=one\nt1.name =two')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('t1'))
        assertEquals('two', project.t1.name)
    }

    @org.junit.Test
    void nonDerefDot() {
        checkProps('alpha.beta.gamma', 'delta.epsilon.mu', 'nu')
        project.propFileLoader.typeCasting = true
        project.ext.set('alpha.beta.gamma', 'eins')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('delta.epsilon.mu=zwei\nnu=pre${alpha.beta.gamma}post')
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('delta.epsilon.mu'))
        assertTrue(project.hasProperty('nu'))
        assertEquals('zwei', project.property('delta.epsilon.mu'))
        assertEquals('preeinspost', project.property('nu'))
    }

    @org.junit.Test
    void targetedPropertiesWithDeferrals() {
        /* Also tests that sys prop settings in the target file work */
        checkProps('aSysProp')

        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('''
tHolder2(com.admc.gradle.MockBean$ThreadHolder) =New Thread Name
tHolder2.heldThread.name =Renamed Thread
sp|aSysProp=werd
        ''')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.systemPropPrefix = 'sp|'
        project.propFileLoader.load(f, null, 'mockBean')
        assertEquals(1, project.propFileLoader.deferredExtensionProps.size())
        project.apply plugin: MockPlugin
        assertEquals(0, project.propFileLoader.deferredExtensionProps.size())
        assertEquals('Renamed Thread', project.mockBean.tHolder2.heldThread.name)
        assertTrue(System.properties.containsKey('aSysProp'))
        assertEquals('werd', System.properties['aSysProp'])
    }

    @org.junit.Test
    void simpleMap() {
        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=one\nbeta=two', 'ISO-8859-1')
        def aMap = [:]
        project.propFileLoader.load(f, aMap)

        assertEquals([alpha: 'one', beta: 'two'], aMap)
    }

    @org.junit.Test
    void appendMap() {
        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=one\nbeta=two', 'ISO-8859-1')
        def aMap = [gamma: 'three']
        project.propFileLoader.load(f, aMap)

        assertEquals([alpha: 'one', gamma: 'three', beta: 'two'], aMap)
    }

    @org.junit.Test(expected=GradleException.class)
    void prohibitOverwriteMap() {
        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=one\nbeta=two', 'ISO-8859-1')
        def aMap = [beta: 'three']
        project.propFileLoader.load(f, aMap)
    }

    @org.junit.Test
    void overwriteMap() {
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=one\nbeta=two', 'ISO-8859-1')
        def aMap = [beta: 'three']
        project.propFileLoader.load(f, aMap)

        assertEquals([alpha: 'one', beta: 'two'], aMap)
    }

    @org.junit.Test
    void castingMap() {
        project.propFileLoader.overwriteThrow = true
        project.propFileLoader.typeCasting = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha(File)=one.txt\nbeta(Float)=9.715', 'ISO-8859-1')
        def aMap = [:]
        project.propFileLoader.load(f, aMap)

        assertEquals(
                [alpha: new File('one.txt'), beta: Float.valueOf(9.715)], aMap)
    }

    @org.junit.Test
    void mapDotAssign() {
        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha.al =one\nbeta.be =two', 'ISO-8859-1')
        def aMap = [:]
        project.propFileLoader.load(f, aMap)

        assertEquals([('alpha.al'): 'one', ('beta.be'): 'two'], aMap)
    }

    @org.junit.Test
    void mapDotMapRef() {
        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('gamma=pre${alpha.al}post\ndelta=pre${beta.be}post', 'ISO-8859-1')
        def aMap = [('alpha.al'): 'one', ('beta.be'): 'two']
        project.propFileLoader.load(f, aMap)

        assertEquals([('alpha.al'): 'one', ('beta.be'): 'two',
                gamma: 'preonepost', delta: 'pretwopost'], aMap)
        
    }

    @org.junit.Test
    void mapProjRef() {
        project.propFileLoader.overwriteThrow = true
        project.ext.set('alpha', 'one')
        project.ext.set('beta', 'two')
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('gamma =pre${alpha}post\ndelta =pre${beta}post', 'ISO-8859-1')
        def aMap = [:]
        project.propFileLoader.load(f, aMap)
        
        assertEquals([gamma: 'preonepost', delta: 'pretwopost'], aMap)
    }

    @org.junit.Test
    void mapEscapedDotProjRef() {
        project.propFileLoader.overwriteThrow = true
        project.ext.set('alpha.al', 'one')
        project.ext.set('beta.be', 'two')
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('gamma =pre${alpha\\\\.al}post\ndelta =pre${beta\\\\.be}post', 'ISO-8859-1')
        def aMap = [:]
        project.propFileLoader.load(f, aMap)
        
        assertEquals([gamma: 'preonepost', delta: 'pretwopost'], aMap)
    }

    @org.junit.Test
    void mapPrefDottedRef() {
        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('gamma.ga=pre${alpha.al}|${pref.beta.be}|${pref.delta.de}post\ndelta.de=four', 'ISO-8859-1')
        def aMap = [('alpha.al'): 'one', ('pref.beta.be'): 'two']
        project.propFileLoader.load(f, 'pref.', aMap)
        
        assertEquals([
            ('alpha.al'): 'one',
            ('pref.beta.be'): 'two',
            ('pref.gamma.ga'): 'preone|two|fourpost',
            ('pref.delta.de'): 'four',
        ], aMap)
    }

    @org.junit.Test
    void behaviorRefPrefixes() {
        checkProps('pref.alpha',
                'pref.beta', 'pref.gamma', 'pref.delta', 'pref.mu')
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('''
alpha=pre${pref.beta}post
beta=one
gamma=pre${-nosuch}post
delta=pre${.nosuch}post
mu=pre${!pref.beta}post
                ''', 'ISO-8859-1')
        project.propFileLoader.load(f, 'pref.')

        assertTrue(project.hasProperty('pref.alpha'))
        assertTrue(project.hasProperty('pref.beta'))
        assertTrue(project.hasProperty('pref.gamma'))
        assertTrue(project.hasProperty('pref.delta'))
        assertTrue(project.hasProperty('pref.mu'))
        assertEquals('preonepost', project.property('pref.alpha'))
        assertEquals('one', project.property('pref.beta'))
        assertEquals('prepost', project.property('pref.gamma'))
        assertEquals('pre${.nosuch}post', project.property('pref.delta'))
        assertEquals('preonepost', project.property('pref.mu'))
    }

    @org.junit.Test(expected=GradleException.class)
    void throwRefPrefix() {
        checkProps('alpha')
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('alpha=pre${!unset}post', 'ISO-8859-1')

        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.NO_SET
        project.propFileLoader.load(f, 'pref.')
    }

    @org.junit.Test
    void escapeRefDollar() {
        checkProps('beta')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('beta=pre${al\\\\$pha}post')
        project.ext.set('al$pha', 'one')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('beta'))
        assertEquals('preonepost', project.property('beta'))
    }

    @org.junit.Test
    void escapeRefCloseCurl() {
        checkProps('beta')

        project.propFileLoader.overwriteThrow = true
        File f = JavaPropFilePluginTest.mkTestFile()
        f.write('beta=pre${al\\\\}pha}post')
        project.ext.set('al}pha', 'one')
        project.propFileLoader.typeCasting = true
        project.propFileLoader.load(f)
        assertTrue(project.hasProperty('beta'))
        assertEquals('preonepost', project.property('beta'))
    }

    @org.junit.Test
    void escapedBehaviorRefPrefixes() {
        // Also tests sysprop-prefixed prop names that beginw ith !, -, .
        File f = JavaPropFilePluginTest.mkTestFile()
        project.propFileLoader.overwriteThrow = true
        checkProps('alpha', 'beta', 'gamma', 'delta', 'epsilon', 'mu')
        f.write('''
alpha=pre${sys|!eins}post
beta=pre${sys|-zwei}post
gamma=pre${sys|.drei}post
delta=pre${\\\\!eins}post
epsilon=pre${\\\\-zwei}post
mu=pre${\\\\.drei}post
                ''', 'ISO-8859-1')
        System.setProperty('!eins', '11')
        System.setProperty('-zwei', '12')
        System.setProperty('.drei', '13')
        project.ext.set('!eins', '21')
        project.ext.set('-zwei', '22')
        project.ext.set('.drei', '23')
        project.propFileLoader.load(f)

        ['alpha', 'beta', 'gamma', 'delta', 'epsilon', 'mu'].each {
            assertFalse("Unexpected System property '$it'",
                    System.properties.containsKey(it))
            assertTrue("Missing property '$it'", project.hasProperty(it))
        }
        assertEquals('pre11post', project.property('alpha'))
        assertEquals('pre12post', project.property('beta'))
        assertEquals('pre13post', project.property('gamma'))
        assertEquals('pre21post', project.property('delta'))
        assertEquals('pre22post', project.property('epsilon'))
        assertEquals('pre23post', project.property('mu'))
    }

    @org.junit.Test
    void noopExpand() {
        assertEquals(
                'Alpha\nBeta', project.propFileLoader.expand('Alpha\nBeta'))
    }

    @org.junit.Test
    void expandFile() {
        URL url = Thread.currentThread().contextClassLoader.getResource(
                'template.txt')
        assert url != null:
            '''Template file not found as resource in classpath: template.txt
'''
        checkProps('aProjProp', 'aSysProp')
        System.setProperty('aSysProp', 'eins')
        project.ext.set('aProjProp', 'zwei')
        File newFile = File.createTempFile('template', '.txt')
        newFile.deleteOnExit()
        newFile.write(url.getText('UTF-8'), 'UTF-8')

        assertEquals('''A Sample Template for Testing the JavaPropFile expand() Method

A Java System property is 'eins', and a Gradle Project property is zwei and again (zwei).
This line contains an un-expanded dot ${.reference} and a removed one in quotes: ''.
''',
                project.propFileLoader.expand(newFile, 'UTF-8'))
    }

    @org.junit.Test
    void expandWithMap() {
        URL url = Thread.currentThread().contextClassLoader.getResource(
                'template.txt')
        assert url != null:
            '''Template file not found as resource in classpath: template.txt
'''
        checkProps('aProjProp', 'aSysProp', 'reference')
        System.setProperty('aSysProp', 'eins')
        project.ext.set('aProjProp', 'zwei')
        File newFile = File.createTempFile('template', '.txt')
        newFile.deleteOnExit()
        newFile.write(url.getText('UTF-8'), 'UTF-8')

        assertEquals('''A Sample Template for Testing the JavaPropFile expand() Method

A Java System property is 'eins', and a Gradle Project property is drei and again (drei).
This line contains an un-expanded dot ${.reference} and a removed one in quotes: ''.
''',
                project.propFileLoader.expand(newFile, [aProjProp: 'drei'], 'UTF-8'))
    }

    @org.junit.Test(expected=GradleException.class)
    void expandMapThrow() {
        checkProps('alpha', 'aSysProp')
        System.setProperty('alpha', 'eins')
        project.ext.set('alpha', 'zwei')
        project.propFileLoader.expand('Pre ${alpha} Post', [other: 'mother'])
    }

    @org.junit.Test
    void expandSomeWithMap() {
        checkProps('alpha', 'beta', 'gamma', 'delta')
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.LITERAL

        assertEquals(
'''One ${alpha} Two ${beta} Three ${alpha} Four drei Five
one ${alpha} two ${beta} three ${alpha} four drei five
'''
                , project.propFileLoader.expand(
'''One ${alpha} Two ${beta} Three ${alpha} Four ${gamma} Five
one ${alpha} two ${beta} three ${alpha} four ${gamma} five
'''
                , [gamma: 'drei', delta: 'vier']))
    }

    @org.junit.Test(expected=GradleException.class)
    void expandBangThrow() {
        checkProps('alpha', 'beta', 'gamma', 'delta')
        project.propFileLoader.unsatisfiedRefBehavior =
                JavaPropFile.Behavior.LITERAL

        project.propFileLoader.expand(
                'One ${!alpha} Two ${!beta} Three ${alpha} Four ${gamma} Five',
                [gamma: 'drei', delta: 'vier'])
    }
}
