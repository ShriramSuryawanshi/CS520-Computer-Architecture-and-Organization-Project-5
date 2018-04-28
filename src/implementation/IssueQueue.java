/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import baseclasses.FunctionalUnitBase;
import baseclasses.InstructionBase;
import baseclasses.Latch;
import baseclasses.ModuleBase;
import baseclasses.PipelineStageBase;
import static baseclasses.PipelineStageBase.operNames;
import baseclasses.PropertiesContainer;
import cpusimulator.CpuSimulator;
import implementation.GlobalData;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import tools.MultiStageDelayUnit;
import tools.MyALU;
import utilitytypes.EnumOpcode;
import utilitytypes.ICpuCore;
import utilitytypes.IFunctionalUnit;
import utilitytypes.IModule;
import utilitytypes.IPipeReg;
import utilitytypes.IPipeStage;
import utilitytypes.IProperties;
import utilitytypes.Logger;
import utilitytypes.Operand;
import voidtypes.VoidProperties;

/**
 *
 * @author millerti
 */
public class IssueQueue extends PipelineStageBase {

    public IssueQueue(ICpuCore core) {
        super(core, "IssueQueue");
    }

    /*    public FloatDiv(IModule parent, String name) {
        super(parent, name);
    } */

 /* private static class MyMathUnit extends PipelineStageBase {

/*        public MyMathUnit(IModule parent) {
            // For simplicity, we just call this stage "in".
            super(parent, "in");
        }*/
    @Override
    public void compute() {

        Latch input = this.readInput(0).duplicate();
        InstructionBase ins = input.getInstruction();
        String instructions = "";

        int issued = -1;
        int total_ins = 0;
        int sent_ins = 0;
        int stall_ins = 0;

        for (int i = 0; i < 256; i++) {

            if (i == 255 && !GlobalData.IQ[i].equals("NULL")) {
                addStatusWord("IQ Full");
                System.exit(0);
            }

            if (GlobalData.IQ[i].equals("NULL") && !ins.isNull()) {
                GlobalData.IQ[i] = input.getInstruction().toString();
                GlobalData.latches[i] = input;
                getCore().incIssued();
                issued = i;
                input.consume();
                break;
            }
        }

        if (GlobalData.Fdiv_sent != -1) {
            GlobalData.Fdiv_sent++;
        }

        for (int i = 0; i < 256; i++) {

            if (!GlobalData.IQ[i].equals("NULL")) {

                ins = GlobalData.latches[i].getInstruction();
                total_ins++;

                Latch output;
                EnumOpcode opcode = ins.getOpcode();
                boolean oper0src = opcode.oper0IsSource();
                Operand oper0 = ins.getOper0();
                Operand src1 = ins.getSrc1();
                Operand src2 = ins.getSrc2();

                String destination = "";

                int output_num;
                if (opcode == EnumOpcode.MUL) {
                    output_num = lookupOutput("IQToIntMul");
                    output = this.newOutput(output_num);

                } else if (opcode == EnumOpcode.DIV || opcode == EnumOpcode.MOD) {
                    output_num = lookupOutput("IQToIntDiv");
                    output = this.newOutput(output_num);

                } else if (opcode == EnumOpcode.FADD || opcode == EnumOpcode.FSUB || opcode == EnumOpcode.FCMP) {
                    output_num = lookupOutput("IQToFloatAddSub");
                    output = this.newOutput(output_num);
                    destination = "in:";

                } else if (opcode == EnumOpcode.FMUL) {
                    output_num = lookupOutput("IQToFloatMul");
                    output = this.newOutput(output_num);
                    destination = "in:";

                } else if (opcode == EnumOpcode.FDIV) {
                    output_num = lookupOutput("IQToFloatDiv");
                    output = this.newOutput(output_num);
                    destination = "FloatDiv:";

                } else if (opcode.accessesMemory()) {
                    output_num = lookupOutput("IQToMemory");
                    output = this.newOutput(output_num);
                    destination = "in:Addr:";

                } else {
                    output_num = lookupOutput("IQToExecute");
                    output = this.newOutput(output_num);
                    destination = "Execute:";
                }

                //   forwardingSearch(GlobalData.latches[i]);
                ICpuCore core = getCore();

                GlobalData.latches[i].deleteProperty("forward0");
                GlobalData.latches[i].deleteProperty("forward1");
                GlobalData.latches[i].deleteProperty("forward2");

                Set<String> fwdSources = core.getForwardingSources();
                // Put operands into array because we will loop over them,
                // searching the pipeline for forwarding opportunities.
                Operand[] operArray = {oper0, src1, src2};

                // For operands that are not registers, getRegisterNumber() will
                // return -1.  We will use that to determine whether or not to
                // look for a given register in the pipeline.
                int[] srcRegs = new int[3];
                // Only want to forward to oper0 if it's a source.
                srcRegs[0] = oper0src ? oper0.getRegisterNumber() : -1;
                srcRegs[1] = src1.getRegisterNumber();
                srcRegs[2] = src2.getRegisterNumber();

                for (int sn = 0; sn < 3; sn++) {
                    int srcRegNum = srcRegs[sn];
                    // Skip any operands that are not register sources
                    if (srcRegNum < 0) {
                        continue;
                    }
                    // Skip any operands that already have values
                    if (operArray[sn].hasValue()) {
                        continue;
                    }
                    Operand oper = operArray[sn];
                    String srcRegName = oper.getRegisterName();
                    String operName = operNames[sn];

                    String srcFoundIn = null;
                    boolean next_cycle = false;
                    boolean this_cycle = false;

                    prn_loop:

                    for (String fwd_pipe_reg_name : fwdSources) {
                        IPipeReg.EnumForwardingStatus fwd_stat = core.matchForwardingRegister(fwd_pipe_reg_name, srcRegNum);

                        switch (fwd_stat) {
                            case NULL:
                                break;
                            case VALID_NOW:
                                srcFoundIn = fwd_pipe_reg_name;
                                this_cycle = true;
                                break prn_loop;
                            case VALID_NEXT_CYCLE:
                                srcFoundIn = fwd_pipe_reg_name;
                                next_cycle = true;
                                break prn_loop;
                        }
                    }

                    if (srcFoundIn != null) {
                        if (!next_cycle) {
                            // If the register number was found and there is a valid
                            // result, go ahead and get the value.
                            int value = core.getResultValue(srcFoundIn);
                            boolean isfloat = core.isResultFloat(srcFoundIn);
                            operArray[sn].setValue(value, isfloat);

                            if (CpuSimulator.printForwarding) {
                                Logger.out.printf("# Forward from " + srcFoundIn + " this cycle to IQ: op" + sn + " of " + ins.toString() + "\n");

                                if (issued != i) {
                                    GlobalData.IQ[i] = GlobalData.latches[i].getInstruction().toString();
                                }
                            }

                        } else {

                            String propname = "forward" + sn;
                            GlobalData.latches[i].setProperty(propname, srcFoundIn);

                        }
                    }
                }

                boolean flag = false;

                for (int sn = 0; sn < 3; sn++) {
                    int srcRegNum = srcRegs[sn];
                    // Skip any operands that are not register sources
                    if (srcRegNum < 0) {
                        continue;
                    }
                    // Skip any that already have values
                    if (operArray[sn].hasValue()) {
                        continue;
                    }

                    String propname = "forward" + sn;
                    if (!GlobalData.latches[i].hasProperty(propname)) {
                        // If any source operand is not available
                        // now or on the next cycle, then stall.

                        flag = true;
                        break;
                    }
                }

                if (flag) {

                    if (issued == i) {
                        instructions = instructions + GlobalData.IQ[i] + " [new] \n";
                    } else if (issued != i) {
                        instructions = instructions + GlobalData.IQ[i] + " \n";
                    }

                    stall_ins++;

                    continue;
                }

                if (!output.canAcceptWork()) {
                    continue;
                }

                //  if (ins.getOpcode() == EnumOpcode.HALT) shutting_down = true;            
                if (CpuSimulator.printForwarding) {
                    for (int sn = 0; sn < 3; sn++) {
                        String propname = "forward" + sn;
                        if (GlobalData.latches[i].hasProperty(propname)) {
                            String operName = PipelineStageBase.operNames[sn];
                            String srcFoundIn = GlobalData.latches[i].getPropertyString(propname);
                            String srcRegName = operArray[sn].getRegisterName();
                            Logger.out.printf("# Posting forward from " + srcFoundIn + " next cycle to " + destination + " op" + sn + " of " + ins.toString() + "\n");
                        }
                    }
                }

                output.copyAllPropertiesFrom(GlobalData.latches[i]);
                output.setInstruction(ins);
                output.write();
                getCore().incDispatched();

                if (issued == i) {
                    instructions = instructions + GlobalData.IQ[i] + " [new] [selected] \n";
                } else if (issued != i) {
                    instructions = instructions + GlobalData.IQ[i] + " [selected] \n";
                }

                if (ins.getOpcode() == EnumOpcode.FDIV) {
                    GlobalData.Fdiv_sent = 0;
                }

                sent_ins++;
                GlobalData.IQ[i] = "NULL";

            }
        }

        for (int a = 0; a < 256; a++) {

            if (!GlobalData.IQ[a].equals("NULL")) {
                continue;

            } else {

                for (int b = a; b < 256; b++) {

                    if (GlobalData.IQ[b].equals("NULL")) {
                        continue;
                    } else {
                        GlobalData.IQ[a] = GlobalData.IQ[b];
                        GlobalData.IQ[b] = "NULL";
                        GlobalData.latches[a] = GlobalData.latches[b];
                        break;
                    }
                }
            }
        }

        setActivity(instructions);

        if (sent_ins == 0 && total_ins > 0 && total_ins == stall_ins && GlobalData.Fdiv_sent > 0) {
            addStatusWord("OutputStall(IQ2FD)");

            if (GlobalData.Fdiv_sent == 15) {
                GlobalData.Fdiv_sent = -1;
            }
        }
    }
}
