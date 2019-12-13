/*******************************************************************************
 * Copyright (c) 2019 TypeFox and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     TypeFox - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.handlers;

import static org.eclipse.lsp4j.SymbolKind.Class;
import static org.eclipse.lsp4j.SymbolKind.Constructor;
import static org.eclipse.lsp4j.SymbolKind.Field;
import static org.eclipse.lsp4j.SymbolKind.Method;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ls.core.internal.ClassFileUtil;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.hover.JavaElementLabels;
import org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest;
import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.Before;
import org.junit.Test;

/**
 * @see CallHierarchyHandler
 */
public class CallHierarchyHandlerTest extends AbstractProjectsManagerBasedTest {

	@Before
	public void setup() throws Exception {
		importProjects(Arrays.asList("eclipse/hello", "maven/salut"));
	}

	@Test
	public void prepareCallHierarchy_noItemAtLocation() throws Exception {
		// Line 16 from `CallHierarchy`
		//<|>/*nothing*/
		String uri = getUriFromSrcProject("org.sample.CallHierarchy");
		assertNull(prepareCallHierarchy(uri, 15, 0));
	}

	@Test
	public void prepareCallHierarchy() throws Exception {
		// Line 15 from `CallHierarchy`
		//    protected int <|>protectedField = 200;
		String uri = getUriFromSrcProject("org.sample.CallHierarchy");
		List<CallHierarchyItem> items = prepareCallHierarchy(uri, 14, 18);
		assertNotNull(items);
		assertEquals(1, items.size());
		assertItem(items.get(0), "protectedField", Field, "", false, 14);
	}

	@Test
	public void prepareCallHierarchy_src_enclosing() throws Exception {
		// Line 20 from `CallHierarchy`
		///*should resolve to enclosing "method/constructor/initializer"*/
		String uri = getUriFromSrcProject("org.sample.CallHierarchy");
		List<CallHierarchyItem> items = prepareCallHierarchy(uri, 19, 0);
		assertNotNull(items);
		assertEquals(1, items.size());
		assertItem(items.get(0), "Base()", Constructor, "", false, 18);
	}

	@Test
	public void incomingCalls_src() throws Exception {
		// Line 27 from `CallHierarchy`
		//    public void <|>bar() {
		String uri = getUriFromSrcProject("org.sample.CallHierarchy");
		List<CallHierarchyItem> items = prepareCallHierarchy(uri, 26, 16);
		assertNotNull(items);
		assertEquals(1, items.size());
		assertItem(items.get(0), "bar()", Method, "void", false, 26);

		List<CallHierarchyIncomingCall> calls = getIncomingCalls(items.get(0));
		assertNotNull(calls);
		assertEquals(3, calls.size());
		assertItem(calls.get(0).getFrom(), "Child()", Constructor, "", false, 42);
		assertItem(calls.get(1).getFrom(), "main(String[])", Method, "void", true, 5);
		assertItem(calls.get(2).getFrom(), "method_1()", Method, "void", false, 33);
	}

	@Test
	public void outgoingCalls_src() throws Exception {
		// Line 34 from `CallHierarchy`
		//    protected void <|>method_1() {
		String uri = getUriFromSrcProject("org.sample.CallHierarchy");
		List<CallHierarchyItem> items = prepareCallHierarchy(uri, 33, 19);
		assertNotNull(items);
		assertEquals(1, items.size());
		assertItem(items.get(0), "method_1()", Method, "void", false, 33);

		List<CallHierarchyOutgoingCall> calls = getOutgoings(items.get(0));
		assertNotNull(calls);
		assertEquals(2, calls.size());
		assertItem(calls.get(0).getTo(), "foo()", Method, "void", false, 22);
		assertItem(calls.get(1).getTo(), "bar()", Method, "void", false, 26);

		List<CallHierarchyOutgoingCall> call1Calls = getOutgoings(calls.get(1).getTo());
		assertNotNull(call1Calls);
		assertEquals(2, call1Calls.size());
		assertItem(call1Calls.get(0).getTo(), "Child()", Constructor, "", false, 42);
		assertItem(call1Calls.get(1).getTo(), "currentThread()", Method, "Thread", false, 0);
	}

	@Test
	public void incomingCalls_maven() throws Exception {
		// Line 12 from `CallHierarchyOther`
		//  @Deprecated public static class <|>X {
		String uri = getUriFromJarProject("org.sample.CallHierarchyOther");
		List<CallHierarchyItem> items = prepareCallHierarchy(uri, 11, 34);
		assertNotNull(items);
		assertEquals(1, items.size());
		assertItem(items.get(0), "X", Class, "", true, 11);

		List<CallHierarchyIncomingCall> calls = getIncomingCalls(items.get(0));
		assertNotNull(calls);
		assertEquals(1, calls.size());
		assertItem(calls.get(0).getFrom(), "FooBuilder()", Constructor, "", false, 9);

		List<CallHierarchyIncomingCall> call0Calls = getIncomingCalls(calls.get(0).getFrom());
		assertNotNull(call0Calls);
		assertEquals(1, call0Calls.size());
		assertItem(call0Calls.get(0).getFrom(), "{...}", Constructor, "", false, 4);
	}

	@Test
	public void outgoing_jar() throws Exception {
		// Line 15 from `CallHierarchy`
		//    public Object <|>build() {
		String uri = getUriFromJarProject("org.sample.CallHierarchy");
		List<CallHierarchyItem> items = prepareCallHierarchy(uri, 14, 18);
		assertNotNull(items);
		assertEquals(1, items.size());
		assertItem(items.get(0), "build()", Method, "Object", false, 14);

		List<CallHierarchyOutgoingCall> calls = getOutgoings(items.get(0));
		assertNotNull(calls);
		assertEquals(1, calls.size());
		assertItem(calls.get(0).getTo(), "capitalize(String)", Method, "String", false, 368);

		List<CallHierarchyOutgoingCall> call0Calls = getOutgoings(calls.get(0).getTo());
		assertNotNull(call0Calls);
		assertEquals(1, call0Calls.size());
		assertItem(call0Calls.get(0).getTo(), "capitalize(String, char...)", Method, "String", false, 401);

		String jarUri = call0Calls.get(0).getTo().getUri();
		assertTrue(jarUri.startsWith("jdt://"));
		assertTrue(jarUri.contains("org.apache.commons.lang3.text"));
		assertTrue(jarUri.contains("WordUtils.class"));
	}

	/**
	 * @param item
	 *            to assert
	 * @param name
	 *            the expected name
	 * @param kind
	 *            the expected kind
	 * @param detail
	 *            the expected detail <b>without</b> the ` : ` (declaration) suffix.
	 * @param deprecated
	 *            expected deprecated state
	 * @param selectionStartLine
	 *            the start line of the selection range
	 * @return the {@code item}
	 */
	static CallHierarchyItem assertItem(CallHierarchyItem item, String name, SymbolKind kind, String detail, boolean deprecated, int selectionStartLine) {
		assertNotNull(item);
		assertEquals(name, item.getName());
		assertEquals(kind, item.getKind());
		if (detail.isEmpty()) {
			assertEquals(detail, item.getDetail());
		} else {
			assertEquals(JavaElementLabels.DECL_STRING + detail, item.getDetail());
		}
		if (deprecated) {
			assertNotNull(item.getTags());
			assertTrue(Stream.of(item.getTags()).anyMatch(tag -> tag == SymbolTag.Deprecated));
		} else {
			assertTrue(item.getTags() == null || item.getTags().length == 0 || !Stream.of(item.getTags()).anyMatch(tag -> tag == SymbolTag.Deprecated));
		}

		assertEquals(selectionStartLine, item.getSelectionRange().getStart().getLine());
		return item;
	}

	static List<CallHierarchyItem> prepareCallHierarchy(String uri, int line, int character) {
		CallHierarchyPrepareParams params = createCallHierarchyPrepareParams(uri, line, character);
		return new CallHierarchyHandler().prepareCallHierarchy(params, new NullProgressMonitor());
	}

	static List<CallHierarchyIncomingCall> getIncomingCalls(CallHierarchyItem item) {
		CallHierarchyIncomingCallsParams params = new CallHierarchyIncomingCallsParams();
		params.setItem(item);
		return new CallHierarchyHandler().callHierarchyIncomingCalls(params, new NullProgressMonitor());
	}

	static List<CallHierarchyOutgoingCall> getOutgoings(CallHierarchyItem item) {
		CallHierarchyOutgoingCallsParams params = new CallHierarchyOutgoingCallsParams();
		params.setItem(item);
		return new CallHierarchyHandler().callHierarchyOutgoingCalls(params, new NullProgressMonitor());
	}

	static CallHierarchyPrepareParams createCallHierarchyPrepareParams(String uri, int line, int character) {
		CallHierarchyPrepareParams params = new CallHierarchyPrepareParams();
		params.setTextDocument(new TextDocumentIdentifier(uri));
		params.setPosition(new Position(line, character));
		return params;
	}

	static String getUriFromJarProject(String className) throws JavaModelException {
		return ClassFileUtil.getURI(WorkspaceHelper.getProject("salut"), className);
	}

	static String getUriFromSrcProject(String className) throws JavaModelException {
		return ClassFileUtil.getURI(WorkspaceHelper.getProject("hello"), className);
	}

}
