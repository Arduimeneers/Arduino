/*
 * This file is part of Arduino.
 *
 * Copyright 2015 Arduino LLC (http://www.arduino.cc/)
 *
 * Arduino is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * As a special exception, you may use this file as part of a free software
 * library without restriction.  Specifically, if other files instantiate
 * templates or use macros or inline functions from this file, or you compile
 * this file and link it with other files to produce an executable, this
 * file does not by itself cause the resulting executable to be covered by
 * the GNU General Public License.  This exception does not however
 * invalidate any other reasons why the executable file might be covered by
 * the GNU General Public License.
 */

package cc.arduino.contributions.libraries.ui;

import cc.arduino.contributions.DownloadableContribution;
import cc.arduino.contributions.libraries.ContributedLibrary;
import cc.arduino.contributions.libraries.LibraryInstaller;
import cc.arduino.contributions.libraries.LibraryTypeComparator;
import cc.arduino.contributions.ui.*;
import cc.arduino.utils.Progress;
import processing.app.BaseNoGui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.function.Predicate;

import static processing.app.I18n.tr;

@SuppressWarnings("serial")
public class LibraryManagerUI extends InstallerJDialog<ContributedLibrary> {

  private final JComboBox typeChooser;
  private final LibraryInstaller installer;
  private Predicate<ContributedLibrary> typeFilter;

  @Override
  protected FilteredAbstractTableModel createContribModel() {
    return new LibrariesIndexTableModel();
  }

  private LibrariesIndexTableModel getContribModel() {
    return (LibrariesIndexTableModel) contribModel;
  }

  @Override
  protected InstallerTableCell createCellRenderer() {
    return new ContributedLibraryTableCell();
  }

  @Override
  protected InstallerTableCell createCellEditor() {
    return new ContributedLibraryTableCell() {
      @Override
      protected void onInstall(ContributedLibrary selectedLibrary, ContributedLibrary installedLibrary) {
        if (selectedLibrary.isReadOnly()) {
          onRemovePressed(installedLibrary);
        } else {
          onInstallPressed(selectedLibrary, installedLibrary);
        }
      }

      @Override
      protected void onRemove(ContributedLibrary library) {
        onRemovePressed(library);
      }
    };
  }

  public LibraryManagerUI(Frame parent, LibraryInstaller installer) {
    super(parent, tr("Library Manager"), Dialog.ModalityType.APPLICATION_MODAL, tr("Unable to reach Arduino.cc due to possible network issues."));
    this.installer = installer;

    filtersContainer.add(new JLabel(tr("Topic")), 1);
    filtersContainer.remove(2);

    typeChooser = new JComboBox();
    typeChooser.setMaximumRowCount(20);
    typeChooser.setEnabled(false);

    filtersContainer.add(Box.createHorizontalStrut(5), 0);
    filtersContainer.add(new JLabel(tr("Type")), 1);
    filtersContainer.add(Box.createHorizontalStrut(5), 2);
    filtersContainer.add(typeChooser, 3);
  }

  protected final ActionListener typeChooserActionListener = new ActionListener() {

    @Override
    public void actionPerformed(ActionEvent event) {
      DropdownItem<ContributedLibrary> selected = (DropdownItem<ContributedLibrary>) typeChooser.getSelectedItem();
      if (typeFilter == null || !typeFilter.equals(selected)) {
        typeFilter = selected.getFilterPredicate();
        if (contribTable.getCellEditor() != null) {
          contribTable.getCellEditor().stopCellEditing();
        }
        updateIndexFilter(filters, categoryFilter, typeFilter);
      }
    }
  };

  @Override
  public void updateIndexFilter(String[] filters, Predicate<ContributedLibrary>... additionalFilters) {
    if (additionalFilters.length == 1) {
      additionalFilters = new Predicate[]{additionalFilters[0], typeFilter};
    }
    super.updateIndexFilter(filters, additionalFilters);
  }

  public void updateUI() {
    DropdownItem<DownloadableContribution> previouslySelectedCategory = (DropdownItem<DownloadableContribution>) categoryChooser.getSelectedItem();
    DropdownItem<DownloadableContribution> previouslySelectedType = (DropdownItem<DownloadableContribution>) typeChooser.getSelectedItem();

    categoryChooser.removeActionListener(categoryChooserActionListener);
    typeChooser.removeActionListener(typeChooserActionListener);

    categoryFilter = null;
    categoryChooser.removeAllItems();

    // Load categories
    categoryChooser.addItem(new DropdownAllItem());
    Collection<String> categories = BaseNoGui.librariesIndexer.getIndex().getCategories();
    for (String category : categories) {
      categoryChooser.addItem(new DropdownLibraryOfCategoryItem(category));
    }

    categoryChooser.setEnabled(categoryChooser.getItemCount() > 1);

    categoryChooser.addActionListener(categoryChooserActionListener);
    if (previouslySelectedCategory != null) {
      categoryChooser.setSelectedItem(previouslySelectedCategory);
    } else {
      categoryChooser.setSelectedIndex(0);
    }

    typeFilter = null;
    typeChooser.removeAllItems();
    typeChooser.addItem(new DropdownAllItem());
    typeChooser.addItem(new DropdownUpdatableLibrariesItem());
    typeChooser.addItem(new DropdownInstalledLibraryItem());
    java.util.List<String> types = new LinkedList<>(BaseNoGui.librariesIndexer.getIndex().getTypes());
    Collections.sort(types, new LibraryTypeComparator());
    for (String type : types) {
      typeChooser.addItem(new DropdownLibraryOfTypeItem(type));
    }
    typeChooser.setEnabled(typeChooser.getItemCount() > 1);
    typeChooser.addActionListener(typeChooserActionListener);
    if (previouslySelectedType != null) {
      typeChooser.setSelectedItem(previouslySelectedType);
    } else {
      typeChooser.setSelectedIndex(0);
    }

    filterField.setEnabled(contribModel.getRowCount() > 0);
  }

  public void selectDropdownItemByClassName(String dropdownItem) {
    selectDropdownItemByClassName(typeChooser, dropdownItem);
  }

  public void setProgress(Progress progress) {
    progressBar.setValue(progress);
  }

  private Thread installerThread = null;

  @Override
  protected void onCancelPressed() {
    super.onUpdatePressed();
    if (installerThread != null) {
      installerThread.interrupt();
    }
  }

  @Override
  protected void onUpdatePressed() {
    super.onUpdatePressed();
    installerThread = new Thread(() -> {
      try {
        setProgressVisible(true, "");
        installer.updateIndex(this::setProgress);
        onIndexesUpdated();
      } catch (Exception e) {
        throw new RuntimeException(e);
      } finally {
        setProgressVisible(false, "");
      }
    });
    installerThread.setUncaughtExceptionHandler(new InstallerJDialogUncaughtExceptionHandler(this, noConnectionErrorMessage));
    installerThread.start();
  }

  public void onInstallPressed(final ContributedLibrary lib, final ContributedLibrary replaced) {
    clearErrorMessage();
    installerThread = new Thread(() -> {
      try {
        setProgressVisible(true, tr("Installing..."));
        installer.install(lib, replaced, this::setProgress);
        onIndexesUpdated(); // TODO: Do a better job in refreshing only the needed element
        //getContribModel().updateLibrary(lib);
      } catch (Exception e) {
        throw new RuntimeException(e);
      } finally {
        setProgressVisible(false, "");
      }
    });
    installerThread.setUncaughtExceptionHandler(new InstallerJDialogUncaughtExceptionHandler(this, noConnectionErrorMessage));
    installerThread.start();
  }

  public void onRemovePressed(final ContributedLibrary lib) {
    boolean managedByIndex = BaseNoGui.librariesIndexer.getIndex().getLibraries().contains(lib);

    if (!managedByIndex) {
      int chosenOption = JOptionPane.showConfirmDialog(this, tr("This library is not listed on Library Manager. You won't be able to reinstall it from here.\nAre you sure you want to delete it?"), tr("Please confirm library deletion"), JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
      if (chosenOption != JOptionPane.YES_OPTION) {
        return;
      }
    }

    clearErrorMessage();
    installerThread = new Thread(() -> {
      try {
        setProgressVisible(true, tr("Removing..."));
        installer.remove(lib, this::setProgress);
        onIndexesUpdated(); // TODO: Do a better job in refreshing only the needed element
        //getContribModel().updateLibrary(lib);
      } catch (Exception e) {
        throw new RuntimeException(e);
      } finally {
        setProgressVisible(false, "");
      }
    });
    installerThread.setUncaughtExceptionHandler(new InstallerJDialogUncaughtExceptionHandler(this, noConnectionErrorMessage));
    installerThread.start();
  }

  protected void onIndexesUpdated() throws Exception {
    // Empty
  }

}
