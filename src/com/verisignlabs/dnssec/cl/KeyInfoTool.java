// $Id: KeyGen.java 1954 2005-08-14 17:05:50Z davidb $
//
// Copyright (C) 2001-2003 VeriSign, Inc.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307
// USA

package com.verisignlabs.dnssec.cl;

import java.io.PrintWriter;
import java.security.interfaces.RSAPublicKey;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.*;
import org.xbill.DNS.DNSKEYRecord;

import com.verisignlabs.dnssec.security.BINDKeyUtils;
import com.verisignlabs.dnssec.security.DnsKeyAlgorithm;
import com.verisignlabs.dnssec.security.DnsKeyPair;

/**
 * This class forms the command line implementation of a DNSSEC DS/DLV
 * generator
 * 
 * @author David Blacka (original)
 * @author $Author: davidb $
 * @version $Revision: 1954 $
 */
public class KeyInfoTool
{

  /**
   * This is a small inner class used to hold all of the command line option
   * state.
   */
  private static class CLIState
  {
    private Options opts;
    public String   keyname    = null;

    public CLIState()
    {
      setupCLI();
    }

    /**
     * Set up the command line options.
     * 
     * @return a set of command line options.
     */
    private void setupCLI()
    {
      opts = new Options();

      // boolean options
      opts.addOption("h", "help", false, "Print this message.");

      // Argument options
      opts.addOption(OptionBuilder.hasOptionalArg().withLongOpt("verbose")
          .withArgName("level")
          .withDescription("verbosity level -- 0 is silence, "
              + "5 is debug information, " + "6 is trace information.\n"
              + "default is level 5.").create('v'));
      
      OptionBuilder.hasArg();
      OptionBuilder.withLongOpt("alg-alias");
      OptionBuilder.withArgName("alias:original:mnemonic");
      OptionBuilder.withDescription("define an alias for an algorithm");
      opts.addOption(OptionBuilder.create('A'));
    }

    public void parseCommandLine(String[] args)
        throws org.apache.commons.cli.ParseException
    {
      CommandLineParser cli_parser = new PosixParser();
      CommandLine cli = cli_parser.parse(opts, args);

      if (cli.hasOption('h')) usage();

      if (cli.hasOption('v'))
      {
        int value = parseInt(cli.getOptionValue('v'), 5);
        Logger rootLogger = Logger.getLogger("");
        switch (value)
        {
          case 0 :
            rootLogger.setLevel(Level.OFF);
            break;
          case 5 :
          default :
            rootLogger.setLevel(Level.FINE);
            break;
          case 6 :
            rootLogger.setLevel(Level.ALL);
            break;
        }
      }

      String[] optstrs;
      if ((optstrs = cli.getOptionValues('A')) != null)
      {
        for (int i = 0; i < optstrs.length; i++)
        {
          addArgAlias(optstrs[i]);
        }
      }
      String[] cl_args = cli.getArgs();

      if (cl_args.length < 1)
      {
        System.err.println("error: missing key file ");
        usage();
      }

      keyname = cl_args[0];
    }

    /** Print out the usage and help statements, then quit. */
    private void usage()
    {
      HelpFormatter f = new HelpFormatter();

      PrintWriter out = new PrintWriter(System.err);

      // print our own usage statement:
      f.printHelp(out,
          75,
          "jdnssec-keyinfo [..options..] keyfile",
          null,
          opts,
          HelpFormatter.DEFAULT_LEFT_PAD,
          HelpFormatter.DEFAULT_DESC_PAD,
          null);

      out.flush();
      System.exit(64);
    }
  }

  /**
   * This is just a convenience method for parsing integers from strings.
   * 
   * @param s the string to parse.
   * @param def the default value, if the string doesn't parse.
   * @return the parsed integer, or the default.
   */
  private static int parseInt(String s, int def)
  {
    try
    {
      int v = Integer.parseInt(s);
      return v;
    }
    catch (NumberFormatException e)
    {
      return def;
    }
  }
  
  private static void addArgAlias(String s)
  {
    if (s == null) return;
    
    DnsKeyAlgorithm algs = DnsKeyAlgorithm.getInstance();
    
    String[] v = s.split(":");
    if (v.length < 2) return;
    
    int alias = parseInt(v[0], -1);
    if (alias <= 0) return;
    int orig = parseInt(v[1], -1);
    if (orig <= 0) return;
    String mn = null;
    if (v.length > 2) mn = v[2];
    
    algs.addAlias(alias, mn, orig);
  }
  
  
  public static void execute(CLIState state) throws Exception
  {

    DnsKeyPair key = BINDKeyUtils.loadKey(state.keyname, null);
    DNSKEYRecord dnskey = key.getDNSKEYRecord();
    DnsKeyAlgorithm dnskeyalg = DnsKeyAlgorithm.getInstance();
    
    boolean isSEP = (dnskey.getFlags() & DNSKEYRecord.Flags.SEP_KEY) != 0;
    
    System.out.println("Name: " + dnskey.getName());
    System.out.println("SEP: " + isSEP);
    
    System.out.println("Algorithm: " + dnskeyalg.algToString(dnskey.getAlgorithm()));
    System.out.println("ID: " + dnskey.getFootprint());
    if (dnskeyalg.baseType(dnskey.getAlgorithm()) == dnskeyalg.RSA)
    {
      RSAPublicKey pub = (RSAPublicKey) key.getPublic();
      System.out.println("RSA Public Exponent: " + pub.getPublicExponent());
      System.out.println("RSA Modulus: " + pub.getModulus());
    }
    
  }

  public static void main(String[] args)
  {
    CLIState state = new CLIState();

    try
    {
      state.parseCommandLine(args);
    }
    catch (UnrecognizedOptionException e)
    {
      System.err.println("error: unknown option encountered: "
          + e.getMessage());
      state.usage();
    }
    catch (AlreadySelectedException e)
    {
      System.err.println("error: mutually exclusive options have "
          + "been selected:\n     " + e.getMessage());
      state.usage();
    }
    catch (Exception e)
    {
      System.err.println("error: unknown command line parsing exception:");
      e.printStackTrace();
      state.usage();
    }

    try
    {
      execute(state);
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
  }
}