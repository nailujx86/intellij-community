// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.ModelReference;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Preprocessor;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

/**
 * This requestor is essentially {@link ModelReferenceSearchParameters} builder.
 */
public interface SearchTargetRequestor {

  /**
   * Sets search scope.<br/>
   * If search scope is left unset then {@link SearchRequestCollector#getParameters() original} scope will be used.
   *
   * @return this object
   */
  @NotNull
  SearchTargetRequestor setSearchScope(@NotNull SearchScope scope);

  /**
   * Restricts search scope to include only selected file types.
   *
   * @return this object
   */
  @NotNull
  SearchTargetRequestor restrictSearchScopeTo(@NotNull FileType... fileTypes);

  /**
   * Orders to search the target. <br/>
   * This method builds {@link ModelReferenceSearchParameters search parameters}
   * and orders {@link SearchRequestCollector#searchSubQuery sub query search}.
   */
  void search();

  /**
   * Orders to search the target. <br/>
   * This method builds {@link ModelReferenceSearchParameters search parameters}
   * and orders {@link SearchRequestCollector#searchSubQuery(Query, Preprocessor) sub query search} with preprocessor.
   */
  void search(@NotNull Preprocessor<ModelReference, ModelReference> preprocessor);
}
