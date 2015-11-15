/*
 * Copyright 2013-2015 Sergey Ignatov, Alexander Zolotov, Florin Patan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.goide;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

public class GoCommentsConverter {
  private static final Pattern LEADING_TAB = Pattern.compile("^\\t", Pattern.MULTILINE);

  public String toHtml(@NotNull List<PsiComment> comments) {
    List<String> strings = ContainerUtil.newArrayList();
    for (PsiComment comment : comments) {
      IElementType type = comment.getTokenType();
      if (type == GoParserDefinition.LINE_COMMENT) {
        strings.add(StringUtil.trimStart(StringUtil.trimStart(comment.getText(), "//"), " "));
      }
      else if (type == GoParserDefinition.MULTILINE_COMMENT) {
        String text = StringUtil.trimEnd(comment.getText(), "*/");
        text = StringUtil.trimStart(text, "/*");
        text = LEADING_TAB.matcher(text).replaceAll("");
        Collections.addAll(strings, StringUtil.splitByLines(text, false));
      }
    }
    return textToHtml(strings);
  }

  @NotNull
  public String textToHtml(@NotNull List<String> strings) {
    State state = new State(strings.iterator());
    while (state.hasNext()) {
      state.next();
      if (state.indented()) {
        state.flushBlock("p");
        processIndentedBlock(state);
      }
      processSimpleBlock(state);
    }
    state.flushBlock("p");
    return state.result();
  }

  private static void processSimpleBlock(@NotNull State state) {
    state.append(state.text);
    if (StringUtil.isEmpty(state.text)) {
      state.flushBlock("p");
      return;
    }
    state.append("\n"); // just for prettier testdata
  }

  private static void processIndentedBlock(@NotNull State state) {
    state.append(state.text);
    int emptyLines = 1;
    String text;
    while ((text = state.next()) != null) {
      if (state.indented()) {
        state.append(StringUtil.repeatSymbol('\n', emptyLines)).append(text);
        emptyLines = 1;
      }
      else if (text.isEmpty()) {
        emptyLines++;
      }
      else {
        break;
      }
    }
    state.flushBlock("pre");
  }

  private static String emphasize(@NotNull String text) {return XmlStringUtil.escapeString(text);}

  private static class State {
    @NotNull private final StringBuilder currentBlock = new StringBuilder();
    @NotNull private final StringBuilder result = new StringBuilder();
    @NotNull private final Iterator<String> iterator;
    @Nullable private String text;

    public State(@NotNull Iterator<String> iterator) {
      this.iterator = iterator;
    }

    String next() {
      return text = iterator.hasNext() ? iterator.next() : null;
    }

    boolean hasNext() {
      return iterator.hasNext();
    }

    boolean indented() {
      return text != null && (StringUtil.startsWithChar(text, ' ') || StringUtil.startsWithChar(text, '\t'));
    }

    State append(@Nullable String text) {
      if (StringUtil.isNotEmpty(text)) {
        currentBlock.append(emphasize(text));
      }
      return this;
    }

    void flushBlock(@NotNull String wrapTag) {
      if (currentBlock.length() > 0) {
        result.append('<').append(wrapTag).append(">").append(currentBlock).append("</").append(wrapTag).append(">\n");
        currentBlock.setLength(0);
      }
    }

    String result() {
      return result.toString();
    }
  }
}
