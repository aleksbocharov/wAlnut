package model.MARK_II.generalAlgorithm.failureResearch.temporalAlgorithms;

import model.MARK_II.generalAlgorithm.ColumnPosition;
import model.MARK_II.generalAlgorithm.Pooler;
import model.MARK_II.generalAlgorithm.SpatialPooler;
import model.MARK_II.generalAlgorithm.failureResearch.spatialAlgorithms.SDRAlgorithm_1;
import model.MARK_II.region.*;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * This is an experimental prediction algorithm. Initial ideas behind how this
 * algorithm was designed are here: https://github.com/WalnutiQ/walnut/issues/199
 *
 * The core idea of this algorithm is that it makes no assumptions about the
 * different transformations that our world has(shifts, rotations, etc.). The
 * algorithm ONLY says if 1 pattern follows another in time in a predictable
 * way, then they are causally related and should have the SAME REPRESENTATION
 * in the brain.
 *
 * This class solidifies those initial ideas into a deterministic prediction
 * algorithm that probably doesn't work well. Fully understanding why this
 * algorithm doesn't predict well will be important to generating new ideas on
 * how to improve it.
 *
 * @author Q Liu (quinnliu@vt.edu)
 * @version 1/16/2016
 */
public class PredictionAlgorithm_1 extends Pooler {
    private SDRAlgorithm_1 SDRAlgorithm_1;

    Set<Neuron> wasActiveNeurons;
    Set<Neuron> isActiveNeurons;

    //Set<Neuron> wasPredictingNeurons;
    Set<Neuron> isPredictingNeurons;

    public PredictionAlgorithm_1(SDRAlgorithm_1 SDRAlgorithm_1) {
        this.SDRAlgorithm_1 = SDRAlgorithm_1;
        super.region = SDRAlgorithm_1.getRegion();

        this.wasActiveNeurons = new HashSet<>();
        this.isActiveNeurons = new HashSet<>();

        //this.wasPredictingNeurons = new HashSet<>();
        this.isPredictingNeurons = new HashSet<>();
    }

    /**
     * Call this method to run PredictionAlgorithm_1 once on a Region.
     *
     * MAIN LOGIC: For each learning neuron in an active column, connect to all
     * previously active neurons.
     */
    public void run() {
        // Step 1) Which neurons to apply logic to?
        // POSSIBLE ANSWER: Iterate through all neurons in active columns in region
        Set<ColumnPosition> activeColumnPositions = this.SDRAlgorithm_1.getActiveColumnPositions();
        for (ColumnPosition ACP : activeColumnPositions) {
            Column activeColumn = super.getRegion().getColumn(ACP.getRow(), ACP.getColumn());
            Neuron learningNeuron = this.getNeuronWithLeastNumberOfConnectedSynapses(activeColumn);

            // Step 2) How do you allow neuronA to predict neuronB will become
            // active in the next time step?
            // POSSIBLE ANSWER: For each learning neuron connect to all
            // previously active neurons. 1 new distal segment per learning neuron.
            DistalSegment distalSegment = new DistalSegment();
            boolean oneOrMorePreviouslyActiveNeuron = false;
            for (Neuron previouslyActiveNeuron : this.wasActiveNeurons) {
                distalSegment.addSynapse(new Synapse<>(previouslyActiveNeuron,
                        Synapse.MINIMAL_CONNECTED_PERMANENCE, 69, 69));
                oneOrMorePreviouslyActiveNeuron = true;
            }

            if (oneOrMorePreviouslyActiveNeuron) {
                //System.out.println("Adding distal segment to neuron at position (" + ACP.getRow() + ", " + ACP.getColumn() + ")");
                learningNeuron.addDistalSegment(distalSegment);
            }

            // Step 3) Which neurons should be active for the current time step?
            // POSSIBLE ANSWER: The active neurons that best represent the
            //                  current sensory input. Since we are currently
            //                  only experimenting with 1 neuron columns just
            //                  return the one neuron.
            Neuron activeNeuron = activeColumn.getNeuron(0);
            activeNeuron.setActiveState(true);
            this.isActiveNeurons.add(activeNeuron);
        }

        // Step 4) What neurons can be used for prediction?
        // POSSIBLE ANSWER: which neurons currently have the most # of connected
        // (NOT active Cells)
        // synapses across all distal dendrites connected to the current set of
        // active neurons. This is where we reward all the competition between
        // all synapses to represent an connection to a past time step.

        // NOTE: connectionScores = sorted # of connected synapses for each neuron in Region
        Set<Integer> connectionScores = this.getConnectionScores();

        int size = this.SDRAlgorithm_1.getActiveColumnPositions().size();
        int index = Math.max(0, connectionScores.size() - size);
        int minimumConnectionScore = (Integer) connectionScores.toArray()[index];

        // Step 5) How many number of predicting neurons?
        // POSSIBLE ANSWER: same number of currently active neurons.
        this.updateIsPredictingNeurons(minimumConnectionScore);

        // Step 6) Which synapse connections should be strengthened to model
        // long term potentiation?
        // POSSIBLE ANSWER: Current time-step is @t=4. Strengthen the
        // connection between neuronBs that isActive @t=4 and isPredicting
        // @t=3 and neuronA that isActive @t=3.
        // TODO: why is neuronB @t = 3.5 isPredicting true and wasPredicting false?
        for (Neuron activeNeuronBatTequals4 : this.isActiveNeurons) {
            if (activeNeuronBatTequals4.getPreviousPredictingState()) {

                for (DistalSegment distalSegment : activeNeuronBatTequals4.getDistalSegments()) {
                    for (Synapse synapse : distalSegment.getSynapses()) {
                        // increase permanence of connection with
                        // neuronAs' active @t=3.
                        if (synapse.getCell().getPreviousActiveState()) {
                            synapse.increasePermanence();
                        }
                    }
                }
            }
        }

        // Step 7) Which synapse connections should be weakened to model
        // long term depression?
        // POSSIBLE ANSWER: Current time-step is @t=4. Weaken the connection
        // between neuronBs that isActive=False @t=4 and isPredicting @t=3
        // and neuronA that isActive @t=3.
        Column[][] columns = super.region.getColumns();
        for (int ri = 0; ri < columns.length; ri++) {
            for (int ci = 0; ci < columns[0].length; ci++) {
                for (Neuron inActiveNeuronBatTequals4 : columns[ri][ci].getNeurons()) {
                    if (!inActiveNeuronBatTequals4.getActiveState() &&
                        inActiveNeuronBatTequals4.getPreviousPredictingState()) {

                        for (DistalSegment distalSegment : inActiveNeuronBatTequals4.getDistalSegments()) {
                            for (Synapse synapse : distalSegment.getSynapses()) {
                                // decrease permanence of connection with
                                // neuronA' active @t=3.
                                if (synapse.getCell().getPreviousActiveState()) {
                                    synapse.decreasePermanence();
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    void updateIsPredictingNeurons(int minimumConnectionScore) {
        for (Neuron activeNeuron : this.isActiveNeurons) {
            Column[][] columns = super.region.getColumns();
            for (int ri = 0; ri < columns.length; ri++) {
                for (int ci = 0; ci < columns[0].length; ci++) {
                    for (Neuron maybePredictingNeuron : columns[ri][ci].getNeurons()) {
                        int connectionScore = this.getNumberOfConnectedSynapsesToCurrentActiveNeuron(maybePredictingNeuron, activeNeuron);

                        // TODO: why is isPredictingNeurons.size = 1 at t = 2.5?
                        if (this.isPredictingNeurons.size() >= this.SDRAlgorithm_1.getActiveColumnPositions().size()) {
                            break;
                        }

                        if (connectionScore >= minimumConnectionScore) {
                            maybePredictingNeuron.setPredictingState(true);
                            this.isPredictingNeurons.add(maybePredictingNeuron);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void nextTimeStep() {
        // NOTE: in order to not call neuron.nextTimeStep() for neurons that
        //       are active and isPredicting sets make isPredictingNeurons set contain
        //       only neurons not already in isActiveNeurons set
        this.isPredictingNeurons.removeAll(this.isActiveNeurons);

        // prepare for next time step by clearing current info that is out of date
        this.wasActiveNeurons.clear();
        for (Neuron neuron : this.isActiveNeurons) {
            this.wasActiveNeurons.add(neuron);
            neuron.nextTimeStep();
        }
        this.isActiveNeurons.clear();

        for (Neuron neuron : this.isPredictingNeurons) {
            neuron.nextTimeStep();
        }
        this.isPredictingNeurons.clear();
    }

    /**
     * @return sorted # of connected synapses for each neuron in Region aka
     * connection scores.
     */
    Set<Integer> getConnectionScores() {
        Set<Integer> connectionScores = new TreeSet<>();

        for (Neuron activeNeuron : this.isActiveNeurons) {
            // We want to figure out which neurons(let's call them
            // futureNeurons) in the Region in any previous time step created a
            // synapse to attach to me(activeNeuron)! This
            // means that in the past after "activeNeuron" was
            // active, then in the next time step "futureNeurons" was
            // active. Thus, if "activeNeuron" is currently
            // active, then this is an INDICATOR that "futureNeurons"
            // will be active in the next time step. Thus we will mark
            // these neurons as "possiblyActiveInNextTimeStep" or
            // "isPredicting".

            // Get # of connected synapses for each neuron in Region for the
            // current set of active neurons.
            Column[][] columns = super.region.getColumns();
            for (int ri = 0; ri < columns.length; ri++) {
                for (int ci = 0; ci < columns[0].length; ci++) {
                    int connectionScore = 0;
                    for (Neuron possiblyActiveInNextTimeStep : columns[ri][ci].getNeurons()) {
                        connectionScore += this.getNumberOfConnectedSynapsesToCurrentActiveNeuron(possiblyActiveInNextTimeStep, activeNeuron);
                    }
                    connectionScores.add(connectionScore);
                }
            }
        }
        return connectionScores;
    }

    int getNumberOfConnectedSynapsesToCurrentActiveNeuron(Neuron possiblyActiveInNextTimeStep, Neuron activeNeuron) {
        // NOTE: This is incredibly inefficient. Fix this by making activeNeuron
        // know which synapses from which neurons are connected to it.
        int numberOfConnectedSynapsesToCurrentActiveNeuron = 0;
        for (DistalSegment distalSegment : possiblyActiveInNextTimeStep.getDistalSegments()) {
            for (Synapse synapse : distalSegment.getConnectedSynapses()) {
                if (synapse.getCell().equals(activeNeuron)) {
                    numberOfConnectedSynapsesToCurrentActiveNeuron++;
                }
            }
        }
        return numberOfConnectedSynapsesToCurrentActiveNeuron;
    }

    /**
     * PROS:
     * 1) Prevents same neuron in column to repeatedly be learning neuron.
     * 2) Evens out connections within Region.
     *
     * CONS:
     * 1) Is this enough of an indicator to say next time neuron2 becomes
     *    active -> neuron3 becomes predicted?
     */
    Neuron getNeuronWithLeastNumberOfConnectedSynapses(Column activeColumn) {
        int minimumNumberOfConnectedSynapses =
                this.getNumberOfConnectedSynapses(activeColumn.getNeuron(0));
        Neuron minimumNeuron = activeColumn.getNeuron(0);
        for (Neuron currentNeuron : activeColumn.getNeurons()) {
            int currentMinimum = this.getNumberOfConnectedSynapses(currentNeuron);
            if (currentMinimum < minimumNumberOfConnectedSynapses) {
                minimumNeuron = currentNeuron;
            }
        }
        return minimumNeuron;
    }

    int getNumberOfConnectedSynapses(Neuron neuron) {
        int numberOfConnectedSynapses = 0;
        for (DistalSegment distalSegment : neuron.getDistalSegments()) {
            numberOfConnectedSynapses += distalSegment.getConnectedSynapses().size();
        }
        return numberOfConnectedSynapses;
    }
}