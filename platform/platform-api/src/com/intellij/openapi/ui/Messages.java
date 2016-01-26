/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.MessageException;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.mac.MacMessages;
import com.intellij.util.Function;
import com.intellij.util.PairFunction;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.util.List;

public class Messages {
  public static final int OK = 0;
  public static final int YES = 0;
  public static final int NO = 1;
  public static final int CANCEL = 2;

  public static final String OK_BUTTON = CommonBundle.getOkButtonText();
  public static final String YES_BUTTON = CommonBundle.getYesButtonText();
  public static final String NO_BUTTON = CommonBundle.getNoButtonText();
  public static final String CANCEL_BUTTON = CommonBundle.getCancelButtonText();

  private static TestDialog ourTestImplementation = TestDialog.DEFAULT;
  private static TestInputDialog ourTestInputImplementation = TestInputDialog.DEFAULT;
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.ui.Messages");


  @TestOnly
  public static TestDialog setTestDialog(TestDialog newValue) {
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      LOG.assertTrue(application.isUnitTestMode(), "This method is available for tests only");
    }
    TestDialog oldValue = ourTestImplementation;
    ourTestImplementation = newValue;
    return oldValue;
  }

  @TestOnly
  public static TestInputDialog setTestInputDialog(TestInputDialog newValue) {
    Application application = ApplicationManager.getApplication();
    if (application != null) {
      LOG.assertTrue(application.isUnitTestMode(), "This method is available for tests only");
    }
    TestInputDialog oldValue = ourTestInputImplementation;
    ourTestInputImplementation = newValue;
    return oldValue;
  }

  @NotNull
  public static Icon getErrorIcon() {
    return UIUtil.getErrorIcon();
  }

  @NotNull
  public static Icon getInformationIcon() {
    return UIUtil.getInformationIcon();
  }

  @NotNull
  public static Icon getWarningIcon() {
    return UIUtil.getWarningIcon();
  }

  @NotNull
  public static Icon getQuestionIcon() {
    return UIUtil.getQuestionIcon();
  }

  public static void installHyperlinkSupport(JTextPane messageComponent) {
    configureMessagePaneUi(messageComponent, "<html></html>");
  }

  @NotNull
  public static JComponent wrapToScrollPaneIfNeeded(@NotNull JComponent comp, int columns, int lines) {
    float fontSize = comp.getFont().getSize2D();
    Dimension maxDim = new Dimension((int)(fontSize * columns), (int)(fontSize * lines));
    Dimension prefDim = comp.getPreferredSize();
    if (prefDim.width <= maxDim.width && prefDim.height <= maxDim.height) return comp;

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(comp);
    scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    int barWidth = UIUtil.getScrollBarWidth();
    Dimension preferredSize =
      new Dimension(Math.min(prefDim.width, maxDim.width) + barWidth,
                    Math.min(prefDim.height, maxDim.height) + barWidth);
    if (prefDim.width > maxDim.width) { //Too wide single-line message should be wrapped
      preferredSize.height = Math.max(preferredSize.height, (int)(4 * fontSize) + barWidth);
    }
    scrollPane.setPreferredSize(preferredSize);
    return scrollPane;
  }

  @NotNull
  public static JTextPane configureMessagePaneUi(JTextPane messageComponent, String message) {
    JTextPane pane = configureMessagePaneUi(messageComponent, message, null);
    if (UIUtil.HTML_MIME.equals(pane.getContentType())) {
      pane.addHyperlinkListener(BrowserHyperlinkListener.INSTANCE);
    }
    return pane;
  }

  @NotNull
  public static JTextPane configureMessagePaneUi(@NotNull JTextPane messageComponent,
                                                 @Nullable String message,
                                                 @Nullable UIUtil.FontSize fontSize) {
    UIUtil.FontSize fixedFontSize = fontSize == null ? UIUtil.FontSize.NORMAL : fontSize;
    messageComponent.setFont(UIUtil.getLabelFont(fixedFontSize));
    if (BasicHTML.isHTMLString(message)) {
      HTMLEditorKit editorKit = new HTMLEditorKit();
      Font font = UIUtil.getLabelFont(fixedFontSize);
      editorKit.getStyleSheet().addRule(UIUtil.displayPropertiesToCSS(font, UIUtil.getLabelForeground()));
      messageComponent.setEditorKit(editorKit);
      messageComponent.setContentType(UIUtil.HTML_MIME);
    }
    messageComponent.setText(message);
    messageComponent.setEditable(false);
    if (messageComponent.getCaret() != null) {
      messageComponent.setCaretPosition(0);
    }

    if (UIUtil.isUnderNimbusLookAndFeel()) {
      messageComponent.setOpaque(false);
      messageComponent.setBackground(UIUtil.TRANSPARENT_COLOR);
    }
    else {
      messageComponent.setBackground(UIUtil.getOptionPaneBackground());
    }

    messageComponent.setForeground(UIUtil.getLabelForeground());
    return messageComponent;
  }

  /**
   * Please, use {@link #showOkCancelDialog} or {@link #showYesNoCancelDialog} if possible (these dialogs implements native OS behavior)!
   *
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showDialog(@Nullable Project project,
                               String message,
                               @Nls(capitalization = Nls.Capitalization.Title) String title,
                               @NotNull String[] options,
                               int defaultOptionIndex,
                               @Nullable Icon icon) {
    return showDialog(project, message, title, options, defaultOptionIndex, icon, null);
  }

  static boolean isApplicationInUnitTestOrHeadless() {
    final Application application = ApplicationManager.getApplication();
    return application != null && !application.isOnAir() &&
           (application.isUnitTestMode() || application.isHeadlessEnvironment());
  }


  /**
   * Please, use {@link #showOkCancelDialog} or {@link #showYesNoCancelDialog} if possible (these dialogs implements native OS behavior)!
   *
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showDialog(@Nullable Project project,
                               String message,
                               @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                               @NotNull String[] options,
                               int defaultOptionIndex,
                               @Nullable Icon icon,
                               @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    return MessagesService.SERVICE.getInstance()
      .showMessageDialog(project, message, title, options, defaultOptionIndex, icon, doNotAskOption);
  }

  /**
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showIdeaMessageDialog(@Nullable Project project,
                                          String message,
                                          @Nls(capitalization = Nls.Capitalization.Title) String title,
                                          @NotNull String[] options,
                                          int defaultOptionIndex,
                                          @Nullable Icon icon,
                                          @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    return MessagesService.SERVICE.getInstance()
      .showIdeaMessageDialog(project, message, title, options, defaultOptionIndex, -1, icon, doNotAskOption);
  }

  public static boolean canShowMacSheetPanel() {
    return MessagesService.SERVICE.getInstance().canShowMacSheetPanel();
  }

  public static boolean isMacSheetEmulation() {
    return MessagesService.SERVICE.getInstance().isMacSheetEmulation();
  }

  /**
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showDialog(Project project,
                               String message,
                               @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                               @Nullable String moreInfo,
                               @NotNull String[] options,
                               int defaultOptionIndex,
                               int focusedOptionIndex,
                               Icon icon) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    return MessagesService.SERVICE.getInstance()
      .showDialog2(project, message, title, moreInfo, options, defaultOptionIndex, focusedOptionIndex, icon);
  }


  /**
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   */
  public static int showDialog(@NotNull Component parent,
                               String message,
                               @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                               @NotNull String[] options,
                               int defaultOptionIndex,
                               @Nullable Icon icon) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    else {
      return MessagesService.SERVICE.getInstance().showDialog3(parent, message, title, options, defaultOptionIndex, icon);
    }
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   * @see #showDialog(Project, String, String, String[], int, Icon, DialogWrapper.DoNotAskOption)
   * @see #showDialog(Component, String, String, String[], int, Icon)
   */
  public static int showDialog(String message,
                               @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                               @NotNull String[] options,
                               int defaultOptionIndex,
                               int focusedOptionIndex,
                               @Nullable Icon icon,
                               @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {

    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    return MessagesService.SERVICE.getInstance()
      .showDialog(message, title, options, defaultOptionIndex, focusedOptionIndex, icon, doNotAskOption);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   * @see #showDialog(Project, String, String, String[], int, Icon)
   * @see #showDialog(Component, String, String, String[], int, Icon)
   */
  public static int showDialog(String message,
                               @Nls(capitalization = Nls.Capitalization.Title) String title,
                               @NotNull String[] options,
                               int defaultOptionIndex,
                               @Nullable Icon icon,
                               @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    return showDialog(message, title, options, defaultOptionIndex, defaultOptionIndex, icon, doNotAskOption);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return number of button pressed: from 0 up to options.length-1 inclusive, or -1 for Cancel
   * @see #showDialog(Project, String, String, String[], int, Icon)
   * @see #showDialog(Component, String, String, String[], int, Icon)
   */
  public static int showDialog(String message,
                               @Nls(capitalization = Nls.Capitalization.Title) String title,
                               @NotNull String[] options,
                               int defaultOptionIndex,
                               @Nullable Icon icon) {
    return showDialog(message, title, options, defaultOptionIndex, icon, null);
  }

  /**
   * @see DialogWrapper#DialogWrapper(Project, boolean)
   */
  public static void showMessageDialog(@Nullable Project project,
                                       String message,
                                       @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON, WindowManager.getInstance().suggestParentWindow(project));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showDialog(project, message, title, new String[]{OK_BUTTON}, 0, icon);
  }

  public static void showMessageDialog(@NotNull Component parent,
                                       String message,
                                       @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON, SwingUtilities.getWindowAncestor(parent));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showDialog(parent, message, title, new String[]{OK_BUTTON}, 0, icon);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showMessageDialog(Project, String, String, Icon)
   * @see #showMessageDialog(Component, String, String, Icon)
   */
  public static void showMessageDialog(String message,
                                       @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON);
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showDialog(message, title, new String[]{OK_BUTTON}, 0, icon);
  }

  @MagicConstant(intValues = {YES, NO})
  public @interface YesNoResult {
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   */
  @YesNoResult
  public static int showYesNoDialog(@Nullable Project project,
                                    String message,
                                    @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                    @NotNull String yesText,
                                    @NotNull String noText,
                                    @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance()
          .showYesNoDialog(title, message, yesText, noText, WindowManager.getInstance().suggestParentWindow(project));
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    int result = showDialog(project, message, title, new String[]{yesText, noText}, 0, icon) == 0 ? YES : NO;
    //noinspection ConstantConditions
    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   */
  @YesNoResult
  public static int showYesNoDialog(@Nullable Project project,
                                    String message,
                                    @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                    @NotNull String yesText,
                                    @NotNull String noText,
                                    @Nullable Icon icon,
                                    @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance()
          .showYesNoDialog(title, message, yesText, noText, WindowManager.getInstance().suggestParentWindow(project), doNotAskOption);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    int result = showDialog(project, message, title, new String[]{yesText, noText}, 0, icon, doNotAskOption) == 0 ? YES : NO;
    //noinspection ConstantConditions
    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   */
  @YesNoResult
  public static int showYesNoDialog(@Nullable Project project,
                                    String message,
                                    @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                    @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance()
          .showYesNoDialog(title, message, YES_BUTTON, NO_BUTTON, WindowManager.getInstance().suggestParentWindow(project));
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    int result = showYesNoDialog(project, message, title, YES_BUTTON, NO_BUTTON, icon);

    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   */
  @YesNoResult
  public static int showYesNoDialog(@Nullable Project project,
                                    String message,
                                    @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                    @Nullable Icon icon,
                                    @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance()
          .showYesNoDialog(title, message, YES_BUTTON, NO_BUTTON, WindowManager.getInstance().suggestParentWindow(project), doNotAskOption);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    int result = showYesNoDialog(project, message, title, YES_BUTTON, NO_BUTTON, icon, doNotAskOption);

    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }


  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   */
  @YesNoResult
  public static int showYesNoDialog(@NotNull Component parent,
                                    String message,
                                    @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                    @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoDialog(title, message, YES_BUTTON, NO_BUTTON, SwingUtilities.getWindowAncestor(parent));
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    int result = showDialog(parent, message, title, new String[]{YES_BUTTON, NO_BUTTON}, 0, icon) == 0 ? YES : NO;
    //noinspection ConstantConditions
    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   * @see #showYesNoDialog(Project, String, String, Icon)
   * @see #showYesNoCancelDialog(Component, String, String, Icon)
   */
  @YesNoResult
  public static int showYesNoDialog(String message,
                                    @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                    @NotNull String yesText,
                                    @NotNull String noText,
                                    @Nullable Icon icon,
                                    @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoDialog(title, message, yesText, noText, null, doNotAskOption);
      }
    }
    catch (MessageException messageException) {
      // just show a dialog instead
    }
    catch (Exception exception) {
      LOG.error(exception);
    }

    int result = showDialog(message, title, new String[]{yesText, noText}, 0, icon, doNotAskOption) == 0 ? YES : NO;
    //noinspection ConstantConditions
    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   * @see #showYesNoDialog(Project, String, String, String, String, Icon)
   * @see #showYesNoDialog(Component, String, String, Icon)
   */
  @YesNoResult
  public static int showYesNoDialog(String message,
                                    @Nls(capitalization = Nls.Capitalization.Title) String title,
                                    String yesText,
                                    String noText,
                                    @Nullable Icon icon) {
    return showYesNoDialog(message, title, yesText, noText, icon, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No" button.
   * @see #showYesNoDialog(Project, String, String, Icon)
   * @see #showYesNoDialog(Component, String, String, Icon)
   */
  @YesNoResult
  public static int showYesNoDialog(String message,
                                    @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                    @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoDialog(title, message, YES_BUTTON, NO_BUTTON, null);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    int result = showYesNoDialog(message, title, YES_BUTTON, NO_BUTTON, icon);
    LOG.assertTrue(result == YES || result == NO, result);
    return result;
  }

  @MagicConstant(intValues = {OK, CANCEL})
  public @interface OkCancelResult {
  }

  /**
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @OkCancelResult
  public static int showOkCancelDialog(Project project,
                                       String message,
                                       @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       @NotNull String okText,
                                       @NotNull String cancelText,
                                       Icon icon,
                                       DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        int result = MacMessages.getInstance()
          .showYesNoDialog(title, message, okText, cancelText, WindowManager.getInstance().suggestParentWindow(project), doNotAskOption);
        return result == YES ? OK : CANCEL;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    return showDialog(project, message, title, new String[]{okText, cancelText}, 0, icon, doNotAskOption) == 0 ? OK : CANCEL;
  }

  /**
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @OkCancelResult
  public static int showOkCancelDialog(Project project,
                                       String message,
                                       @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       @NotNull String okText,
                                       @NotNull String cancelText,
                                       Icon icon) {
    return showOkCancelDialog(project, message, title, okText, cancelText, icon, null);
  }

  /**
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @OkCancelResult
  public static int showOkCancelDialog(Project project,
                                       String message,
                                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       Icon icon) {
    return showOkCancelDialog(project, message, title, OK_BUTTON, CANCEL_BUTTON, icon);
  }

  /**
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @OkCancelResult
  public static int showOkCancelDialog(@NotNull Component parent,
                                       String message,
                                       @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       @NotNull String okText,
                                       @NotNull String cancelText,
                                       Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        int result =
          MacMessages.getInstance().showYesNoDialog(title, message, okText, cancelText, SwingUtilities.getWindowAncestor(parent));
        return result == YES ? OK : CANCEL;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    return showDialog(parent, message, title, new String[]{okText, cancelText}, 0, icon) == 0 ? OK : CANCEL;
  }

  /**
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @OkCancelResult
  public static int showOkCancelDialog(@NotNull Component parent,
                                       String message,
                                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       Icon icon) {
    return showOkCancelDialog(parent, message, title, OK_BUTTON, CANCEL_BUTTON, icon);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   * @see #showOkCancelDialog(Project, String, String, Icon)
   * @see #showOkCancelDialog(Component, String, String, Icon)
   */
  @OkCancelResult
  public static int showOkCancelDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, Icon icon) {
    return showOkCancelDialog(message, title, OK_BUTTON, CANCEL_BUTTON, icon, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   * @see #showOkCancelDialog(Project, String, String, String, String, Icon)
   * @see #showOkCancelDialog(Component, String, String, String, String, Icon)
   */
  @OkCancelResult
  public static int showOkCancelDialog(String message,
                                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       String okText,
                                       String cancelText,
                                       Icon icon) {
    return showOkCancelDialog(message, title, okText, cancelText, icon, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #OK} if user pressed "Ok" or {@link #CANCEL} if user pressed "Cancel" button.
   * @see #showOkCancelDialog(Project, String, String, String, String, Icon, DialogWrapper.DoNotAskOption)
   * @see #showOkCancelDialog(Component, String, String, String, String, Icon)
   */
  @OkCancelResult
  public static int showOkCancelDialog(String message,
                                       @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       @NotNull String okText,
                                       @NotNull String cancelText,
                                       Icon icon,
                                       @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        int result = MacMessages.getInstance().showYesNoDialog(title, message, okText, cancelText, null, doNotAskOption);
        return result == YES ? OK : CANCEL;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    return showDialog(message, title, new String[]{okText, cancelText}, 0, icon, doNotAskOption) == 0 ? OK : CANCEL;
  }

  public static int showCheckboxOkCancelDialog(String message,
                                               @Nls(capitalization = Nls.Capitalization.Title) String title,
                                               String checkboxText,
                                               final boolean checked,
                                               final int defaultOptionIndex,
                                               final int focusedOptionIndex,
                                               Icon icon) {
    return showCheckboxMessageDialog(message, title, new String[]{OK_BUTTON, CANCEL_BUTTON}, checkboxText, checked, defaultOptionIndex,
                                     focusedOptionIndex, icon, new PairFunction<Integer, JCheckBox, Integer>() {
        @Override
        public Integer fun(final Integer exitCode, final JCheckBox cb) {
          return exitCode == -1 ? CANCEL : exitCode + (cb.isSelected() ? 1 : 0);
        }
      });
  }

  public static int showCheckboxMessageDialog(String message,
                                              @Nls(capitalization = Nls.Capitalization.Title) String title,
                                              @NotNull String[] options,
                                              String checkboxText,
                                              final boolean checked,
                                              final int defaultOptionIndex,
                                              final int focusedOptionIndex,
                                              Icon icon,
                                              @Nullable final PairFunction<Integer, JCheckBox, Integer> exitFunc) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    else {
      return MessagesService.SERVICE.getInstance()
        .showTwoStepConfirmationDialog(message, title, options, checkboxText, checked, defaultOptionIndex, focusedOptionIndex, icon,
                                       exitFunc);
    }
  }


  public static int showTwoStepConfirmationDialog(String message,
                                                  @Nls(capitalization = Nls.Capitalization.Title) String title,
                                                  String checkboxText,
                                                  Icon icon) {
    return showCheckboxMessageDialog(message, title, new String[]{OK_BUTTON}, checkboxText, true, -1, -1, icon, null);
  }

  public static void showErrorDialog(@Nullable Project project,
                                     @Nls String message,
                                     @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, OK_BUTTON, WindowManager.getInstance().suggestParentWindow(project));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showDialog(project, message, title, new String[]{OK_BUTTON}, 0, getErrorIcon());
  }

  public static void showErrorDialog(@NotNull Component component,
                                     String message,
                                     @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, OK_BUTTON, SwingUtilities.getWindowAncestor(component));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showDialog(component, message, title, new String[]{OK_BUTTON}, 0, getErrorIcon());
  }

  public static void showErrorDialog(@NotNull Component component, String message) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance()
          .showErrorDialog(CommonBundle.getErrorTitle(), message, OK_BUTTON, SwingUtilities.getWindowAncestor(component));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showDialog(component, message, CommonBundle.getErrorTitle(), new String[]{OK_BUTTON}, 0, getErrorIcon());
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showErrorDialog(Project, String, String)
   * @see #showErrorDialog(Component, String, String)
   */
  public static void showErrorDialog(String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, OK_BUTTON, null);
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showDialog(message, title, new String[]{OK_BUTTON}, 0, getErrorIcon());
  }

  public static void showWarningDialog(@Nullable Project project,
                                       String message,
                                       @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, OK_BUTTON, WindowManager.getInstance().suggestParentWindow(project));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showDialog(project, message, title, new String[]{OK_BUTTON}, 0, getWarningIcon());
  }

  public static void showWarningDialog(@NotNull Component component,
                                       String message,
                                       @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, OK_BUTTON, SwingUtilities.getWindowAncestor(component));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showDialog(component, message, title, new String[]{OK_BUTTON}, 0, getWarningIcon());
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showWarningDialog(Project, String, String)
   * @see #showWarningDialog(Component, String, String)
   */
  public static void showWarningDialog(String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showErrorDialog(title, message, OK_BUTTON, null);
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showDialog(message, title, new String[]{OK_BUTTON}, 0, getWarningIcon());
  }

  @MagicConstant(intValues = {YES, NO, CANCEL})
  public @interface YesNoCancelResult {
  }


  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(Project project,
                                          String message,
                                          @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                          @NotNull String yes,
                                          @NotNull String no,
                                          @NotNull String cancel,
                                          @Nullable Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance()
          .showYesNoCancelDialog(title, message, yes, no, cancel, WindowManager.getInstance().suggestParentWindow(project), null);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    int buttonNumber = showDialog(project, message, title, new String[]{yes, no, cancel}, 0, icon);
    return buttonNumber == 0 ? YES : buttonNumber == 1 ? NO : CANCEL;
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(Project project,
                                          String message,
                                          @Nls(capitalization = Nls.Capitalization.Title) String title,
                                          Icon icon) {
    return showYesNoCancelDialog(project, message, title, YES_BUTTON, NO_BUTTON, CANCEL_BUTTON, icon);
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(@NotNull Component parent,
                                          String message,
                                          @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                          @NotNull String yes,
                                          @NotNull String no,
                                          @NotNull String cancel,
                                          Icon icon) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance()
          .showYesNoCancelDialog(title, message, yes, no, cancel, SwingUtilities.getWindowAncestor(parent), null);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    int buttonNumber = showDialog(parent, message, title, new String[]{yes, no, cancel}, 0, icon);
    return buttonNumber == 0 ? YES : buttonNumber == 1 ? NO : CANCEL;
  }

  /**
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(@NotNull Component parent,
                                          String message,
                                          @Nls(capitalization = Nls.Capitalization.Title) String title,
                                          Icon icon) {
    return showYesNoCancelDialog(parent, message, title, YES_BUTTON, NO_BUTTON, CANCEL_BUTTON, icon);
  }


  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   * @see #showYesNoCancelDialog(Project, String, String, String, String, String, Icon)
   * @see #showYesNoCancelDialog(Component, String, String, String, String, String, Icon)
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(String message,
                                          @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title,
                                          @NotNull String yes,
                                          @NotNull String no,
                                          @NotNull String cancel,
                                          Icon icon,
                                          @Nullable DialogWrapper.DoNotAskOption doNotAskOption) {
    try {
      if (canShowMacSheetPanel()) {
        return MacMessages.getInstance().showYesNoCancelDialog(title, message, yes, no, cancel, null, doNotAskOption);
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    int buttonNumber = showDialog(message, title, new String[]{yes, no, cancel}, 0, icon, doNotAskOption);
    return buttonNumber == 0 ? YES : buttonNumber == 1 ? NO : CANCEL;
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   * @see #showYesNoCancelDialog(Project, String, String, String, String, String, Icon)
   * @see #showYesNoCancelDialog(Component, String, String, String, String, String, Icon)
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(String message,
                                          @Nls(capitalization = Nls.Capitalization.Title) String title,
                                          String yes,
                                          String no,
                                          String cancel,
                                          Icon icon) {
    return showYesNoCancelDialog(message, title, yes, no, cancel, icon, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @return {@link #YES} if user pressed "Yes" or {@link #NO} if user pressed "No", or {@link #CANCEL} if user pressed "Cancel" button.
   * @see #showYesNoCancelDialog(Project, String, String, Icon)
   * @see #showYesNoCancelDialog(Component, String, String, Icon)
   */
  @YesNoCancelResult
  public static int showYesNoCancelDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, Icon icon) {
    return showYesNoCancelDialog(message, title, YES_BUTTON, NO_BUTTON, CANCEL_BUTTON, icon);
  }

  /**
   * @return trimmed input string or <code>null</code> if user cancelled dialog.
   */
  @Nullable
  public static String showPasswordDialog(@Nls String message, @Nls(capitalization = Nls.Capitalization.Title) String title) {
    return showPasswordDialog(null, message, title, null, null);
  }

  /**
   * @return trimmed input string or <code>null</code> if user cancelled dialog.
   */
  @Nullable
  public static String showPasswordDialog(Project project,
                                          @Nls String message,
                                          @Nls(capitalization = Nls.Capitalization.Title) String title,
                                          @Nullable Icon icon) {
    return showPasswordDialog(project, message, title, icon, null);
  }

  /**
   * @return trimmed input string or <code>null</code> if user cancelled dialog.
   */
  @Nullable
  public static String showPasswordDialog(@Nullable Project project,
                                          @Nls String message,
                                          @Nls(capitalization = Nls.Capitalization.Title) String title,
                                          @Nullable Icon icon,
                                          @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
    }

    return MessagesService.SERVICE.getInstance().showPasswordDialog(project, message, title, icon, validator);
  }

  /**
   * @return trimmed input string or <code>null</code> if user cancelled dialog.
   */
  @Nullable
  public static String showInputDialog(@Nullable Project project,
                                       String message,
                                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       @Nullable Icon icon) {
    return showInputDialog(project, message, title, icon, null, null);
  }

  /**
   * @return trimmed input string or <code>null</code> if user cancelled dialog.
   */
  @Nullable
  public static String showInputDialog(@NotNull Component parent,
                                       String message,
                                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       @Nullable Icon icon) {
    return showInputDialog(parent, message, title, icon, null, null);
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showInputDialog(Project, String, String, Icon)
   * @see #showInputDialog(Component, String, String, Icon)
   */
  @Nullable
  public static String showInputDialog(String message, @Nls(capitalization = Nls.Capitalization.Title) String title, @Nullable Icon icon) {
    return showInputDialog(message, title, icon, null, null);
  }

  @Nullable
  public static String showInputDialog(@Nullable Project project,
                                       @Nls String message,
                                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       @Nullable Icon icon,
                                       @Nullable String initialValue,
                                       @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
    }
    else {
      return MessagesService.SERVICE.getInstance().showInputDialog(project, message, title, icon, initialValue, validator);
    }
  }

  @Nullable
  public static String showInputDialog(Project project,
                                       @Nls String message,
                                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       @Nullable Icon icon,
                                       @Nullable String initialValue,
                                       @Nullable InputValidator validator,
                                       @Nullable TextRange selection) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
    }
    else {
      return MessagesService.SERVICE.getInstance().showInputDialog2(project, message, title, icon, initialValue, validator, selection);
    }
  }

  @Nullable
  public static String showInputDialog(@NotNull Component parent,
                                       String message,
                                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       @Nullable Icon icon,
                                       @Nullable String initialValue,
                                       @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
    }
    else {
      return MessagesService.SERVICE.getInstance().showInputDialog3(parent, message, title, icon, initialValue, validator);
    }
  }

  /**
   * Use this method only if you do not know project or component
   *
   * @see #showInputDialog(Project, String, String, Icon, String, InputValidator)
   * @see #showInputDialog(Component, String, String, Icon, String, InputValidator)
   */
  @Nullable
  public static String showInputDialog(String message,
                                       @Nls(capitalization = Nls.Capitalization.Title) String title,
                                       @Nullable Icon icon,
                                       @Nullable String initialValue,
                                       @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
    }
    else {
      return MessagesService.SERVICE.getInstance().showInputDialog4(message, title, icon, initialValue, validator);
    }
  }

  @Nullable
  public static String showMultilineInputDialog(Project project,
                                                String message,
                                                @Nls(capitalization = Nls.Capitalization.Title) String title,
                                                @Nullable String initialValue,
                                                @Nullable Icon icon,
                                                @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
    }
    return MessagesService.SERVICE.getInstance().showMultilineInputDialog(project, message, title, initialValue, icon, validator);
  }

  @NotNull
  public static Pair<String, Boolean> showInputDialogWithCheckBox(String message,
                                                                  @Nls(capitalization = Nls.Capitalization.Title) String title,
                                                                  String checkboxText,
                                                                  boolean checked,
                                                                  boolean checkboxEnabled,
                                                                  @Nullable Icon icon,
                                                                  @NonNls String initialValue,
                                                                  @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return new Pair<String, Boolean>(ourTestInputImplementation.show(message), checked);
    }
    else {
      return MessagesService.SERVICE.getInstance()
        .showInputDialogWithCheckBox(message, title, checkboxText, checked, checkboxEnabled, icon, initialValue, validator);
    }
  }

  @Nullable
  public static String showEditableChooseDialog(String message,
                                                @Nls(capitalization = Nls.Capitalization.Title) String title,
                                                @Nullable Icon icon,
                                                String[] values,
                                                String initialValue,
                                                @Nullable InputValidator validator) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestInputImplementation.show(message);
    }
    else {
      return MessagesService.SERVICE.getInstance().showEditableChooseDialog(message, title, icon, values, initialValue, validator);
    }
  }

  /**
   * @deprecated It looks awful!
   */
  @Deprecated
  public static int showChooseDialog(String message,
                                     @Nls(capitalization = Nls.Capitalization.Title) String title,
                                     String[] values,
                                     String initialValue,
                                     @Nullable Icon icon) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    else {
      return MessagesService.SERVICE.getInstance().showChooseDialog(message, title, values, initialValue, icon);
    }
  }

  /**
   * @deprecated It looks awful!
   */
  @Deprecated
  public static int showChooseDialog(@NotNull Component parent,
                                     String message,
                                     @Nls(capitalization = Nls.Capitalization.Title) String title,
                                     String[] values,
                                     String initialValue,
                                     Icon icon) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    else {
      return MessagesService.SERVICE.getInstance().showChooseDialog2(parent, message, title, values, initialValue, icon);
    }
  }

  /**
   * @see DialogWrapper#DialogWrapper(Project, boolean)
   * @deprecated It looks awful!
   */
  @Deprecated
  public static int showChooseDialog(Project project,
                                     String message,
                                     @Nls(capitalization = Nls.Capitalization.Title) String title,
                                     Icon icon,
                                     String[] values,
                                     String initialValue) {
    if (isApplicationInUnitTestOrHeadless()) {
      return ourTestImplementation.show(message);
    }
    else {
      return MessagesService.SERVICE.getInstance().showChooseDialog3(project, message, title, icon, values, initialValue);
    }
  }

  /**
   * Shows dialog with given message and title, information icon {@link #getInformationIcon()} and OK button
   */
  public static void showInfoMessage(Component component,
                                     String message,
                                     @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON, SwingUtilities.getWindowAncestor(component));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showMessageDialog(component, message, title, getInformationIcon());
  }

  /**
   * Shows dialog with given message and title, information icon {@link #getInformationIcon()} and OK button
   */
  public static void showInfoMessage(@Nullable Project project,
                                     @Nls String message,
                                     @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON, WindowManager.getInstance().suggestParentWindow(project));
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showMessageDialog(project, message, title, getInformationIcon());
  }

  /**
   * Shows dialog with given message and title, information icon {@link #getInformationIcon()} and OK button
   * <p/>
   * Use this method only if you do not know project or component
   *
   * @see #showInputDialog(Project, String, String, Icon, String, InputValidator)
   * @see #showInputDialog(Component, String, String, Icon, String, InputValidator)
   */
  public static void showInfoMessage(String message, @NotNull @Nls(capitalization = Nls.Capitalization.Title) String title) {
    try {
      if (canShowMacSheetPanel()) {
        MacMessages.getInstance().showOkMessageDialog(title, message, OK_BUTTON, null);
        return;
      }
    }
    catch (MessageException ignored) {/*rollback the message and show a dialog*/}
    catch (Exception reportThis) {
      LOG.error(reportThis);
    }

    showMessageDialog(message, title, getInformationIcon());
  }

  /**
   * Shows dialog with text area to edit long strings that don't fit in text field.
   */
  public static void showTextAreaDialog(final JTextField textField,
                                        final @Nls(capitalization = Nls.Capitalization.Title) String title,
                                        @NonNls final String dimensionServiceKey,
                                        final Function<String, List<String>> parser,
                                        final Function<List<String>, String> lineJoiner) {
    if (isApplicationInUnitTestOrHeadless()) {
      ourTestImplementation.show(title);
    }
    else {
      MessagesService.SERVICE.getInstance().showTextAreaDialog(textField, title, dimensionServiceKey, parser, lineJoiner);
    }
  }

  public static void showTextAreaDialog(final JTextField textField,
                                        final @Nls(capitalization = Nls.Capitalization.Title) String title,
                                        @NonNls final String dimensionServiceKey) {
    showTextAreaDialog(textField, title, dimensionServiceKey, ParametersListUtil.DEFAULT_LINE_PARSER,
                       ParametersListUtil.DEFAULT_LINE_JOINER);
  }
}
