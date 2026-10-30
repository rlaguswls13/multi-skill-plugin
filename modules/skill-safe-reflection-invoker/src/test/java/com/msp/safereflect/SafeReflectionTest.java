package com.msp.safereflect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SafeReflectionTest {

    static class SamplePerson {
        private String name;
        private int age;

        public SamplePerson() {
            this("Default", 0);
        }

        public SamplePerson(String name, int age) {
            this.name = name;
            this.age = age;
        }

        private String secretGreeting(String title) {
            return "Hello, " + title + " " + name + " (age " + age + ")";
        }
    }

    @Test
    @DisplayName("Instantiate object via reflection dynamically")
    void testNewInstance() {
        SamplePerson person = SafeReflection.newInstance(SamplePerson.class, "Alice", 30);
        assertNotNull(person);
        assertEquals("Alice", SafeReflection.of(person).getFieldValue("name"));
        assertEquals(30, (int) SafeReflection.of(person).getFieldValue("age"));
    }

    @Test
    @DisplayName("Get and set private fields seamlessly")
    void testFieldAccess() {
        SamplePerson person = new SamplePerson("Bob", 25);
        SafeReflection ref = SafeReflection.of(person);

        assertEquals("Bob", ref.getFieldValue("name"));

        ref.setFieldValue("name", "Robert");
        ref.setFieldValue("age", 26);

        assertEquals("Robert", ref.getFieldValue("name"));
        assertEquals(26, (int) ref.getFieldValue("age"));
    }

    @Test
    @DisplayName("Invoke private method without checked exception hassle")
    void testMethodInvocation() {
        SamplePerson person = new SamplePerson("Charlie", 40);
        String result = SafeReflection.of(person).invokeMethod("secretGreeting", "Dr.");

        assertEquals("Hello, Dr. Charlie (age 40)", result);
    }
}
