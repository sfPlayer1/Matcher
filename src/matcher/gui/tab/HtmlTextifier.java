// This file is partially derived from the ASM library. The ASM library itself is licensed as follows:
//
// ASM: a very small and fast Java bytecode manipulation framework
// Copyright (c) 2000-2011 INRIA, France Telecom
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
// 1. Redistributions of source code must retain the above copyright
//    notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright
//    notice, this list of conditions and the following disclaimer in the
//    documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holders nor the names of its
//    contributors may be used to endorse or promote products derived from
//    this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
// THE POSSIBILITY OF SUCH DAMAGE.

package matcher.gui.tab;

import java.util.List;
import java.util.ListIterator;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.Textifier;

import matcher.NameType;
import matcher.srcprocess.HtmlUtil;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MethodInstance;

final class HtmlTextifier extends Textifier {
	HtmlTextifier(ClassInstance cls, NameType nameType) {
		super(Opcodes.ASM9);

		this.cls = cls;
		this.nameType = nameType;
	}

	@Override
	public Textifier visitField(int access, String name, String descriptor, String signature, Object value) {
		FieldInstance field = cls.getField(name, descriptor, nameType);
		if (field != null) text.add(String.format("<div id=\"%s\">", HtmlUtil.getId(field)));

		Textifier ret = super.visitField(access, name, descriptor, signature, value);

		if (field != null) text.add("</div>");

		return ret;
	}

	@Override
	public Textifier visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		MethodInstance method = cls.getMethod(name, descriptor, nameType);
		if (method != null) text.add(String.format("<div id=\"%s\">", HtmlUtil.getId(method)));

		Textifier ret = super.visitMethod(access, name, descriptor, signature, exceptions);

		if (method != null) text.add("</div>");

		return ret;
	}

	@Override
	public void visitClassEnd() {
		escape(text);
	}

	@SuppressWarnings("unchecked")
	private static Object escape(Object o) {
		if (o instanceof List) {
			for (ListIterator<Object> it = ((List<Object>) o).listIterator(); it.hasNext(); ) {
				it.set(escape(it.next()));
			}
		} else if (o instanceof String) {
			String str = (String) o;

			if (!str.startsWith("<div ") && !str.equals("</div>")) {
				o = HtmlUtil.escape(str);
			}
		} else {
			throw new IllegalStateException("unexpected object type: "+o.getClass());
		}

		return o;
	}

	@Override
	protected Textifier createTextifier() {
		return new HtmlTextifier(cls, nameType);
	}

	private final ClassInstance cls;
	private final NameType nameType;
}
