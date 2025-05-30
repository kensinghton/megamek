/*
 * Copyright (C) 2017 - The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MegaMek.
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 */
package megamek.client.ui.dialogs.customMek;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import megamek.client.generator.RandomCallsignGenerator;
import megamek.client.generator.RandomGenderGenerator;
import megamek.client.generator.RandomNameGenerator;
import megamek.client.ui.GBC;
import megamek.client.ui.Messages;
import megamek.client.ui.dialogs.iconChooser.PortraitChooserDialog;
import megamek.client.ui.dialogs.customMek.CustomMekDialog;
import megamek.common.Entity;
import megamek.common.EntitySelector;
import megamek.common.Infantry;
import megamek.common.LAMPilot;
import megamek.common.ProtoMek;
import megamek.common.Tank;
import megamek.common.enums.Gender;
import megamek.common.icons.Portrait;
import megamek.common.options.OptionsConstants;
import megamek.common.preference.PreferenceManager;

/**
 * Controls for customizing crew in the chat lounge. For most crew types this is part of the pilot tab.
 * For multi-crew cockpits there is a separate tab for each crew member and another that shows common options
 * for the entire crew.
 *
 * @author Neoancient
 */
public class CustomPilotViewPanel extends JPanel {
    private static final long serialVersionUID = 345126674612500365L;

    private final Entity entity;
    private Gender gender = Gender.RANDOMIZE;

    private final JCheckBox chkMissing = new JCheckBox(Messages.getString("CustomMekDialog.chkMissing"));
    private final JTextField fldName = new JTextField(20);
    private final JTextField fldNick = new JTextField(20);
    private final JTextField fldHits = new JTextField(5);
    private final JCheckBox chkClanPilot = new JCheckBox(Messages.getString("CustomMekDialog.chkClanPilot"));
    private final JTextField fldGunnery = new JTextField(3);
    private final JTextField fldGunneryL = new JTextField(3);
    private final JTextField fldGunneryM = new JTextField(3);
    private final JTextField fldGunneryB = new JTextField(3);
    private final JTextField fldPiloting = new JTextField(3);
    private final JTextField fldGunneryAero = new JTextField(3);
    private final JTextField fldGunneryAeroL = new JTextField(3);
    private final JTextField fldGunneryAeroM = new JTextField(3);
    private final JTextField fldGunneryAeroB = new JTextField(3);
    private final JTextField fldPilotingAero = new JTextField(3);
    private final JTextField fldArtillery = new JTextField(3);
    private final JTextField fldTough = new JTextField(3);
    private final JTextField fldFatigue = new JTextField(3);

    private final JComboBox<String> cbBackup = new JComboBox<>();

    private final List<Entity> entityUnitNum = new ArrayList<>();
    private final JComboBox<String> choUnitNum = new JComboBox<>();

    private Portrait portrait;

    public CustomPilotViewPanel(CustomMekDialog parent, Entity entity, int slot, boolean editable) {
        this.entity = entity;
        setLayout(new GridBagLayout());
        JLabel label;

        if (entity.getCrew().getSlotCount() > 1) {
            chkMissing.setActionCommand("missing");
            chkMissing.addActionListener(parent);
            chkMissing.addActionListener(e -> missingToggled());
            chkMissing.setSelected(entity.getCrew().isMissing(slot));
            add(chkMissing, GBC.eop());
        }

        if (parent.getClientGUI() != null) {
            JButton portraitButton = new JButton();
            portraitButton.setPreferredSize(new Dimension(72, 72));
            portraitButton.setName("portrait");
            portraitButton.addActionListener(e -> {
                final PortraitChooserDialog portraitDialog = new PortraitChooserDialog(
                        parent.getFrame(), entity.getCrew().getPortrait(slot));
                if (portraitDialog.showDialog().isConfirmed()) {
                    portrait = portraitDialog.getSelectedItem();
                    portraitButton.setIcon(portraitDialog.getSelectedItem().getImageIcon());
                }
            });

            portrait = entity.getCrew().getPortrait(slot);
            portraitButton.setIcon(entity.getCrew().getPortrait(slot).getImageIcon());
            add(portraitButton, GBC.std().gridheight(4));

            JButton button = new JButton(Messages.getString("CustomMekDialog.RandomName"));
            button.addActionListener(e -> {
                gender = RandomGenderGenerator.generate();
                fldName.setText(RandomNameGenerator.getInstance().generate(gender, isClanPilot(), entity.getOwner().getName()));
            });
            add(button, GBC.eop());

            button = new JButton(Messages.getString("CustomMekDialog.RandomCallsign"));
            button.addActionListener(e -> fldNick.setText(RandomCallsignGenerator.getInstance().generate()));
            add(button, GBC.eop());

            button = new JButton(Messages.getString("CustomMekDialog.RandomSkill"));
            button.addActionListener(e -> {
                int[] skills = parent.getClient().getSkillGenerator().generateRandomSkills(entity);
                fldGunnery.setText(Integer.toString(skills[0]));
                fldPiloting.setText(Integer.toString(skills[1]));
                if (entity.getCrew() instanceof LAMPilot) {
                    skills = parent.getClient().getSkillGenerator().generateRandomSkills(entity);
                    fldGunneryAero.setText(Integer.toString(skills[0]));
                    fldPilotingAero.setText(Integer.toString(skills[1]));
                }
            });
            add(button, GBC.eop());

        }
        add(chkClanPilot, GBC.eop());
        chkClanPilot.setSelected(entity.getCrew().isClanPilot(slot));

        label = new JLabel(Messages.getString("CustomMekDialog.labName"), SwingConstants.RIGHT);
        add(label, GBC.std());
        add(fldName, GBC.eol());
        fldName.setText(entity.getCrew().getName(slot));

        label = new JLabel(Messages.getString("CustomMekDialog.labNick"), SwingConstants.RIGHT);
        add(label, GBC.std());
        add(fldNick, GBC.eol());
        fldNick.setText(entity.getCrew().getNickname(slot));

        label = new JLabel(Messages.getString("CustomMekDialog.labHits"), SwingConstants.RIGHT);
        add(label, GBC.std());
        add(fldHits, GBC.eop());
        fldHits.setText(String.valueOf(entity.getCrew().getHits()));

        if (parent.getClient().getGame().getOptions().booleanOption(OptionsConstants.RPG_RPG_GUNNERY)) {
            label = new JLabel(Messages.getString("CustomMekDialog.labGunneryL"), SwingConstants.RIGHT);
            add(label, GBC.std());
            add(fldGunneryL, GBC.eol());

            label = new JLabel(Messages.getString("CustomMekDialog.labGunneryM"), SwingConstants.RIGHT);
            add(label, GBC.std());
            add(fldGunneryM, GBC.eol());

            label = new JLabel(Messages.getString("CustomMekDialog.labGunneryB"), SwingConstants.RIGHT);
            add(label, GBC.std());
            add(fldGunneryB, GBC.eol());

            if (entity.getCrew() instanceof LAMPilot) {
                label = new JLabel(Messages.getString("CustomMekDialog.labGunneryAeroL"), SwingConstants.RIGHT);
                add(label, GBC.std());
                add(fldGunneryAeroL, GBC.eol());

                label = new JLabel(Messages.getString("CustomMekDialog.labGunneryAeroM"), SwingConstants.RIGHT);
                add(label, GBC.std());
                add(fldGunneryAeroM, GBC.eol());

                label = new JLabel(Messages.getString("CustomMekDialog.labGunneryAeroB"), SwingConstants.RIGHT);
                add(label, GBC.std());
                add(fldGunneryAeroB, GBC.eol());
            }

        } else {
            label = new JLabel(Messages.getString("CustomMekDialog.labGunnery"), SwingConstants.RIGHT);
            add(label, GBC.std());
            add(fldGunnery, GBC.eol());

            if (entity.getCrew() instanceof LAMPilot) {
                label = new JLabel(Messages.getString("CustomMekDialog.labGunneryAero"), SwingConstants.RIGHT);
                add(label, GBC.std());
                add(fldGunneryAero, GBC.eol());
            }
        }
        if (entity.getCrew() instanceof LAMPilot) {
            LAMPilot pilot = (LAMPilot) entity.getCrew();
            fldGunneryL.setText(Integer.toString(pilot.getGunneryMekL()));
            fldGunneryM.setText(Integer.toString(pilot.getGunneryMekM()));
            fldGunneryB.setText(Integer.toString(pilot.getGunneryMekB()));
            fldGunnery.setText(Integer.toString(pilot.getGunneryMek()));
            fldGunneryAeroL.setText(Integer.toString(pilot.getGunneryAeroL()));
            fldGunneryAeroM.setText(Integer.toString(pilot.getGunneryAeroM()));
            fldGunneryAeroB.setText(Integer.toString(pilot.getGunneryAeroB()));
            fldGunneryAero.setText(Integer.toString(pilot.getGunneryAero()));
        } else {
            fldGunneryL.setText(Integer.toString(entity.getCrew().getGunneryL(slot)));
            fldGunneryM.setText(Integer.toString(entity.getCrew().getGunneryM(slot)));
            fldGunneryB.setText(Integer.toString(entity.getCrew().getGunneryB(slot)));
            fldGunnery.setText(Integer.toString(entity.getCrew().getGunnery(slot)));
            fldGunneryAeroL.setText("0");
            fldGunneryAeroM.setText("0");
            fldGunneryAeroB.setText("0");
            fldGunneryAero.setText("0");
        }

        label = new JLabel(Messages.getString("CustomMekDialog.labPiloting"), SwingConstants.RIGHT);
        if (entity instanceof Tank) {
            label.setText(Messages.getString("CustomMekDialog.labDriving"));
        } else if (entity instanceof Infantry) {
            label.setText(Messages.getString("CustomMekDialog.labAntiMek"));
        }
        if (entity.getCrew() instanceof LAMPilot) {
            add(label, GBC.std());
            add(fldPiloting, GBC.eol());
            fldPiloting.setText(Integer.toString(((LAMPilot) entity.getCrew()).getPilotingMek()));
            label = new JLabel(Messages.getString("CustomMekDialog.labPilotingAero"), SwingConstants.RIGHT);
            add(label, GBC.std());
            add(fldPilotingAero, GBC.eop());
            fldPilotingAero.setText(Integer.toString(((LAMPilot) entity.getCrew()).getPilotingAero()));
        } else {
            add(label, GBC.std());
            add(fldPiloting, GBC.eop());
            fldPiloting.setText(Integer.toString(entity.getCrew().getPiloting(slot)));
            fldPilotingAero.setText("0");
        }

        if (parent.getClient().getGame().getOptions().booleanOption(OptionsConstants.RPG_ARTILLERY_SKILL)) {
            label = new JLabel(Messages.getString("CustomMekDialog.labArtillery"), SwingConstants.RIGHT);
            add(label, GBC.std());
            add(fldArtillery, GBC.eop());
        }
        fldArtillery.setText(Integer.toString(entity.getCrew().getArtillery(slot)));

        if (parent.getClient().getGame().getOptions().booleanOption(OptionsConstants.RPG_TOUGHNESS)) {
            label = new JLabel(Messages.getString("CustomMekDialog.labTough"), SwingConstants.RIGHT);
            add(label, GBC.std());
            add(fldTough, GBC.eop());
        }
        fldTough.setText(Integer.toString(entity.getCrew().getToughness(slot)));

        if (parent.getClient().getGame().getOptions().booleanOption(OptionsConstants.ADVANCED_TACOPS_FATIGUE)) {
            label = new JLabel(Messages.getString("CustomMekDialog.labFatigue"), SwingConstants.RIGHT);
            add(label, GBC.std());
            add(fldFatigue, GBC.eop());
        }
        fldFatigue.setText(Integer.toString(entity.getCrew().getCrewFatigue(slot)));

        if (entity.getCrew().getSlotCount() > 2) {
            for (int i = 0; i < entity.getCrew().getSlotCount(); i++) {
                if (i != slot) {
                    cbBackup.addItem(entity.getCrew().getCrewType().getRoleName(i));
                }
            }
            if (slot == entity.getCrew().getCrewType().getPilotPos()) {
                label = new JLabel(Messages.getString("CustomMekDialog.labBackupPilot"), SwingConstants.RIGHT);
                add(label, GBC.std());
                add(cbBackup, GBC.eop());
                cbBackup.setToolTipText(Messages.getString("CustomMekDialog.tooltipBackupPilot"));
                cbBackup.setSelectedItem(entity.getCrew().getCrewType().getRoleName(entity.getCrew().getBackupPilotPos()));
            } else if (slot == entity.getCrew().getCrewType().getGunnerPos()) {
                label = new JLabel(Messages.getString("CustomMekDialog.labBackupGunner"), SwingConstants.RIGHT);
                add(label, GBC.std());
                add(cbBackup, GBC.eop());
                cbBackup.setToolTipText(Messages.getString("CustomMekDialog.tooltipBackupGunner"));
                cbBackup.setSelectedItem(entity.getCrew().getCrewType().getRoleName(entity.getCrew().getBackupGunnerPos()));
            }
        }

        if (entity instanceof ProtoMek) {
            // All ProtoMeks have a callsign.
            String callsign = Messages.getString("CustomMekDialog.Callsign") + ": " +
                    (entity.getUnitNumber() + PreferenceManager
                            .getClientPreferences().getUnitStartChar()) +
                    '-' + entity.getId();
            label = new JLabel(callsign, SwingConstants.CENTER);
            add(label, GBC.eol().anchor(GridBagConstraints.CENTER));

            // Get the ProtoMeks of this entity's player
            // that *aren't* in the entity's unit.
            Iterator<Entity> otherUnitEntities = parent.getClient().getGame()
                    .getSelectedEntities(new EntitySelector() {
                        private final int ownerId = entity.getOwnerId();

                        private final short unitNumber = entity.getUnitNumber();

                        @Override
                        public boolean accept(Entity unitEntity) {
                            return (unitEntity instanceof ProtoMek)
                                    && (ownerId == unitEntity.getOwnerId())
                                    && (unitNumber != unitEntity.getUnitNumber());
                        }
                    });

            // If we got any other entities, show the unit number controls.
            if (otherUnitEntities.hasNext()) {
                label = new JLabel(Messages.getString("CustomMekDialog.labUnitNum"), SwingConstants.CENTER);
                add(choUnitNum, GBC.eop());
                refreshUnitNum(otherUnitEntities);
            }
        }

        if (!editable) {
            fldName.setEnabled(false);
            fldNick.setEnabled(false);
            chkClanPilot.setEnabled(false);
            fldHits.setEnabled(false);
            fldGunnery.setEnabled(false);
            fldGunneryL.setEnabled(false);
            fldGunneryM.setEnabled(false);
            fldGunneryB.setEnabled(false);
            fldGunneryAero.setEnabled(false);
            fldGunneryAeroL.setEnabled(false);
            fldGunneryAeroM.setEnabled(false);
            fldGunneryAeroB.setEnabled(false);
            fldPiloting.setEnabled(false);
            fldPilotingAero.setEnabled(false);
            fldArtillery.setEnabled(false);
            fldTough.setEnabled(false);
            fldFatigue.setEnabled(false);
        }

        missingToggled();
    }

    /**
     * Populate the list of entities in other units from the given enumeration.
     *
     * @param others
     *            the <code>Enumeration</code> containing entities in other
     *            units.
     */
    private void refreshUnitNum(Iterator<Entity> others) {
        // Clear the list of old values
        choUnitNum.removeAllItems();
        entityUnitNum.clear();

        // Make an entry for "no change".
        choUnitNum.addItem(Messages.getString("CustomMekDialog.doNotSwapUnits"));
        entityUnitNum.add(entity);

        // Walk through the other entities.
        while (others.hasNext()) {
            // Track the position of the next other entity.
            final Entity other = others.next();
            entityUnitNum.add(other);

            // Show the other entity's name and callsign.
            String callsign = other.getDisplayName() + " (" +
                    (other.getUnitNumber() + PreferenceManager.getClientPreferences().getUnitStartChar())
                    + '-' + other.getId() + ')';
            choUnitNum.addItem(callsign);
        }
        choUnitNum.setSelectedIndex(0);
    }

    public boolean getMissing() {
        return chkMissing.isSelected();
    }

    public String getPilotName() {
        return fldName.getText();
    }

    public String getNickname() {
        return fldNick.getText();
    }

    public String getHits() {
        int hits;
        try {
            hits = Integer.parseInt(fldHits.getText());
            if (hits < 0) {
                hits = 0;
            } else if (hits > 5) {
                hits = 6;
            }
        } catch (NumberFormatException e) {
            hits = 0;
        }
        // Update field then return
        fldHits.setText(String.valueOf(hits));
        return fldHits.getText();
    }

    public Gender getGender() {
        return gender;
    }

    public boolean isClanPilot() {
        return chkClanPilot.isSelected();
    }

    public int getGunnery() {
        return Integer.parseInt(fldGunnery.getText());
    }

    public int getGunneryL() {
        return Integer.parseInt(fldGunneryL.getText());
    }

    public int getGunneryM() {
        return Integer.parseInt(fldGunneryM.getText());
    }

    public int getGunneryB() {
        return Integer.parseInt(fldGunneryB.getText());
    }

    public int getGunneryAero() {
        return Integer.parseInt(fldGunneryAero.getText());
    }

    public int getGunneryAeroL() {
        return Integer.parseInt(fldGunneryAeroL.getText());
    }

    public int getGunneryAeroM() {
        return Integer.parseInt(fldGunneryAeroM.getText());
    }

    public int getGunneryAeroB() {
        return Integer.parseInt(fldGunneryAeroB.getText());
    }

    public int getArtillery() {
        return Integer.parseInt(fldArtillery.getText());
    }

    public int getPiloting() {
        return Integer.parseInt(fldPiloting.getText());
    }

    public int getPilotingAero() {
        return Integer.parseInt(fldPilotingAero.getText());
    }

    public int getToughness() {
        return Integer.parseInt(fldTough.getText());
    }

    public int getCrewFatigue() {
        return Integer.parseInt(fldFatigue.getText());
    }

    public Portrait getPortrait() {
        return portrait;
    }

    public Entity getEntityUnitNumSwap() {
        if (entityUnitNum.isEmpty() || (choUnitNum.getSelectedIndex() <= 0)) {
            return null;
        }
        return entityUnitNum.get(choUnitNum.getSelectedIndex());
    }

    public int getBackup() {
        if (null != cbBackup.getSelectedItem()) {
            for (int i = 0; i < entity.getCrew().getSlotCount(); i++) {
                if (cbBackup.getSelectedItem().equals(entity.getCrew().getCrewType().getRoleName(i))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void missingToggled() {
        for (int i = 0; i < getComponentCount(); i++) {
            if (!getComponent(i).equals(chkMissing)) {
                getComponent(i).setEnabled(!chkMissing.isSelected());
            }
        }
    }

    public void enableMissing(boolean enable) {
        chkMissing.setEnabled(enable);
    }
}
