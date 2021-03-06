/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jimfs;

import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;

/**
 * Tests for {@link AttributeService}.
 *
 * @author Colin Decker
 */
@RunWith(JUnit4.class)
public class AttributeServiceTest {

  private AttributeService service;

  @Before
  public void setUp() {
    ImmutableSet<AttributeProvider> providers = ImmutableSet.of(
        StandardAttributeProviders.get("basic"),
        StandardAttributeProviders.get("owner"),
        new TestAttributeProvider());
    service = new AttributeService(providers, ImmutableMap.<String, Object>of());
  }

  @Test
  public void testSupportedFileAttributeViews() {
    ASSERT.that(service.supportedFileAttributeViews())
        .is(ImmutableSet.of("basic", "test", "owner"));
  }

  @Test
  public void testSupportsFileAttributeView() {
    ASSERT.that(service.supportsFileAttributeView(BasicFileAttributeView.class)).isTrue();
    ASSERT.that(service.supportsFileAttributeView(TestAttributeView.class)).isTrue();
    ASSERT.that(service.supportsFileAttributeView(PosixFileAttributeView.class)).isFalse();
  }

  @Test
  public void testSetInitialAttributes() {
    File file = Directory.create(0);
    service.setInitialAttributes(file);

    ASSERT.that(file.getAttributeNames("test")).has().exactly("bar", "baz");
    ASSERT.that(file.getAttributeNames("owner")).has().exactly("owner");

    ASSERT.that(service.getAttribute(file, "basic:lastModifiedTime")).isA(FileTime.class);
    ASSERT.that(file.getAttribute("test", "bar")).is(0L);
    ASSERT.that(file.getAttribute("test", "baz")).is(1);
  }

  @Test
  public void testGetAttribute() {
    File file = Directory.create(0);
    service.setInitialAttributes(file);

    ASSERT.that(service.getAttribute(file, "test:foo")).is("hello");
    ASSERT.that(service.getAttribute(file, "test", "foo")).is("hello");
    ASSERT.that(service.getAttribute(file, "basic:isRegularFile")).is(false);
    ASSERT.that(service.getAttribute(file, "isDirectory")).is(true);
    ASSERT.that(service.getAttribute(file, "test:baz")).is(1);
  }

  @Test
  public void testGetAttribute_fromInheritedProvider() {
    File file = Directory.create(0);
    ASSERT.that(service.getAttribute(file, "test:isRegularFile")).is(false);
    ASSERT.that(service.getAttribute(file, "test:isDirectory")).is(true);
    ASSERT.that(service.getAttribute(file, "test", "fileKey")).is(0);
  }

  @Test
  public void testGetAttribute_failsForAttributesNotDefinedByProvider() {
    File file = Directory.create(0);
    try {
      service.getAttribute(file, "test:blah");
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      // baz is defined by "test", but basic doesn't inherit test
      service.getAttribute(file, "basic", "baz");
      fail();
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void testSetAttribute() {
    File file = Directory.create(0);
    service.setAttribute(file, "test:bar", 10L, false);
    ASSERT.that(file.getAttribute("test", "bar")).is(10L);

    service.setAttribute(file, "test:baz", 100, false);
    ASSERT.that(file.getAttribute("test", "baz")).is(100);
  }

  @Test
  public void testSetAttribute_forInheritedProvider() {
    File file = Directory.create(0);
    service.setAttribute(file, "test:lastModifiedTime", FileTime.fromMillis(0), false);
    ASSERT.that(file.getAttribute("test", "lastModifiedTime")).isNull();
    ASSERT.that(service.getAttribute(file, "basic:lastModifiedTime")).is(FileTime.fromMillis(0));
  }

  @Test
  public void testSetAttribute_withAlternateAcceptedType() {
    File file = Directory.create(0);
    service.setAttribute(file, "test:bar", 10F, false);
    ASSERT.that(file.getAttribute("test", "bar")).is(10L);

    service.setAttribute(file, "test:bar", BigInteger.valueOf(123), false);
    ASSERT.that(file.getAttribute("test", "bar")).is(123L);
  }

  @Test
  public void testSetAttribute_onCreate() {
    File file = Directory.create(0);
    service.setInitialAttributes(file, new BasicFileAttribute<>("test:baz", 123));
    ASSERT.that(file.getAttribute("test", "baz")).is(123);
  }

  @Test
  public void testSetAttribute_failsForAttributesNotDefinedByProvider() {
    File file = Directory.create(0);
    service.setInitialAttributes(file);

    try {
      service.setAttribute(file, "test:blah", "blah", false);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    try {
      // baz is defined by "test", but basic doesn't inherit test
      service.setAttribute(file, "basic:baz", 5, false);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ASSERT.that(file.getAttribute("test", "baz")).is(1);
  }

  @Test
  public void testSetAttribute_failsForArgumentThatIsNotOfCorrectType() {
    File file = Directory.create(0);
    service.setInitialAttributes(file);
    try {
      service.setAttribute(file, "test:bar", "wrong", false);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ASSERT.that(file.getAttribute("test", "bar")).is(0L);
  }

  @Test
  public void testSetAttribute_failsForNullArgument() {
    File file = Directory.create(0);
    service.setInitialAttributes(file);
    try {
      service.setAttribute(file, "test:bar", null, false);
      fail();
    } catch (NullPointerException expected) {
    }

    ASSERT.that(file.getAttribute("test", "bar")).is(0L);
  }

  @Test
  public void testSetAttribute_failsForAttributeThatIsNotSettable() {
    File file = Directory.create(0);
    try {
      service.setAttribute(file, "test:foo", "world", false);
      fail();
    } catch (IllegalArgumentException expected) {
    }

    ASSERT.that(file.getAttribute("test", "foo")).isNull();
  }

  @Test
  public void testSetAttribute_onCreate_failsForAttributeThatIsNotSettableOnCreate() {
    File file = Directory.create(0);
    try {
      service.setInitialAttributes(file, new BasicFileAttribute<>("test:foo", "world"));
      fail();
    } catch (IllegalArgumentException expected) {
      // IAE because test:foo just can't be set
    }

    try {
      service.setInitialAttributes(file, new BasicFileAttribute<>("test:bar", 5));
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void testGetFileAttributeView() throws IOException {
    final File file = Directory.create(0);
    service.setInitialAttributes(file);

    FileLookup fileLookup = new FileLookup() {
      @Override
      public File lookup() throws IOException {
        return file;
      }
    };

    ASSERT.that(service.getFileAttributeView(fileLookup, TestAttributeView.class))
        .isNotNull();
    ASSERT.that(service.getFileAttributeView(fileLookup, BasicFileAttributeView.class))
        .isNotNull();

    TestAttributes attrs
        = service.getFileAttributeView(fileLookup, TestAttributeView.class).readAttributes();
    ASSERT.that(attrs.foo()).is("hello");
    ASSERT.that(attrs.bar()).is(0);
    ASSERT.that(attrs.baz()).is(1);
  }

  @Test
  public void testGetFileAttributeView_isNullForUnsupportedView() {
    final File file = Directory.create(0);
    FileLookup fileLookup = new FileLookup() {
      @Override
      public File lookup() throws IOException {
        return file;
      }
    };
    ASSERT.that(service.getFileAttributeView(fileLookup, PosixFileAttributeView.class))
        .isNull();
  }

  @Test
  public void testReadAttributes_asMap() {
    File file = Directory.create(0);
    service.setInitialAttributes(file);

    ImmutableMap<String, Object> map = service.readAttributes(file, "test:foo,bar,baz");
    ASSERT.that(map).isEqualTo(
        ImmutableMap.of(
            "foo", "hello",
            "bar", 0L,
            "baz", 1));

    FileTime time = service.getAttribute(file, "basic:creationTime");

    map = service.readAttributes(file, "test:*");
    ASSERT.that(map).isEqualTo(
        ImmutableMap.<String, Object>builder()
            .put("foo", "hello")
            .put("bar", 0L)
            .put("baz", 1)
            .put("fileKey", 0)
            .put("isDirectory", true)
            .put("isRegularFile", false)
            .put("isSymbolicLink", false)
            .put("isOther", false)
            .put("size", 0L)
            .put("lastModifiedTime", time)
            .put("lastAccessTime", time)
            .put("creationTime", time)
            .build());

    map = service.readAttributes(file, "basic:*");
    ASSERT.that(map).isEqualTo(
        ImmutableMap.<String, Object>builder()
            .put("fileKey", 0)
            .put("isDirectory", true)
            .put("isRegularFile", false)
            .put("isSymbolicLink", false)
            .put("isOther", false)
            .put("size", 0L)
            .put("lastModifiedTime", time)
            .put("lastAccessTime", time)
            .put("creationTime", time)
            .build());
  }

  @Test
  public void testReadAttributes_asMap_failsForInvalidAttributes() {
    File file = Directory.create(0);
    try {
      service.readAttributes(file, "basic:fileKey,isOther,*,creationTime");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("invalid attributes");
    }

    try {
      service.readAttributes(file, "basic:fileKey,isOther,foo");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("invalid attribute");
    }
  }

  @Test
  public void testReadAttributes_asObject() {
    File file = Directory.create(0);
    service.setInitialAttributes(file);

    BasicFileAttributes basicAttrs = service.readAttributes(file, BasicFileAttributes.class);
    ASSERT.that(basicAttrs.fileKey()).is(0);
    ASSERT.that(basicAttrs.isDirectory()).isTrue();
    ASSERT.that(basicAttrs.isRegularFile()).isFalse();

    TestAttributes testAttrs = service.readAttributes(file, TestAttributes.class);
    ASSERT.that(testAttrs.foo()).is("hello");
    ASSERT.that(testAttrs.bar()).is(0);
    ASSERT.that(testAttrs.baz()).is(1);

    file.setAttribute("test", "baz", 100);
    ASSERT.that(service.readAttributes(file, TestAttributes.class).baz()).is(100);
  }

  @Test
  public void testReadAttributes_failsForUnsupportedAttributesType() {
    File file = Directory.create(0);
    try {
      service.readAttributes(file, PosixFileAttributes.class);
      fail();
    } catch (UnsupportedOperationException expected) {
    }
  }

  @Test
  public void testIllegalAttributeFormats() {
    File file = Directory.create(0);
    try {
      service.getAttribute(file, ":bar");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("attribute format");
    }

    try {
      service.getAttribute(file, "test:");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("attribute format");
    }

    try {
      service.getAttribute(file, "basic:test:isDirectory");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("attribute format");
    }

    try {
      service.getAttribute(file, "basic:fileKey,size");
      fail();
    } catch (IllegalArgumentException expected) {
      ASSERT.that(expected.getMessage()).contains("single attribute");
    }
  }
}
