#!/usr/bin/perl -w

use strict;

die "Error: No command specified\n" unless scalar(@ARGV);
my $username= (getpwuid($<))[0];

# TODO: use getopt long to offer slick selection of walltime, nondeletion of
# job file, capture of stdout/stderr etc.
my $walltime;

# We could use tmpnam to generate temporary files, but it's probably a good
# idea to err on the side of not cluttering up /tmp with these things.
my $homeDir = $ENV{HOME};
my $jobfileDir = "$homeDir/jobfiles/";
mkdir $jobfileDir unless -d $jobfileDir;
die "Error creating directory $jobfileDir: $!" if $?;
my $jobfile= $jobfileDir.'singlenode.job.'.$$;

# This is what gets displayed in the output of "qstat -n" and suchlike
my $jobname = $ARGV[0];
$jobname =~ s/.*\///;

# Number of CPU cores to allocate (there are 16 per backend node)
my $ppn=1;

open F, ">$jobfile" or die "Error: Unable to write to temporary job file '$jobfile'\n";
print F "#!/bin/sh\n";
#print F "#PBS -e /dev/null -o /dev/null -N $jobname -l nodes=1:ppn=$ppn\n";
print F "#PBS -e $homeDir/stderr -o $homeDir/stdout -N $jobname -l nodes=1:ppn=$ppn\n";
print F "#PBS -l walltime=$walltime:00:00\n" if defined $walltime;
#print F "#PBS -l walltime=5000:00:00\n";
#print F "#PBS -l walltime=00:05:00\n";
print F "#PBS -M ".$username."\@cs.cmu.edu -m abe\n";
print F "#PBS -q pool2\n";
print F "cd \$PBS_O_WORKDIR\n";
print F "@ARGV\n";
close F;

my $qsubcmd = "qsub -q pool2 $jobfile";
my $jobid = `$qsubcmd`;
die "Error executing '$qsubcmd': $!\n" if $?;

# Quick and dirty way to report the job ID and node on which it's running.

# TODO: It won't be assigned a node right away, so wait a few seconds to see if a node shows up in the output of qstat.  If it doesn't, then either there was some problem we didn't detect or the user will have to wait until the job can be run.
chomp $jobid;
if ($jobid =~ /^(\d+)\./) {
    my $number = $1;
    my $stuff = `qstat -n | grep -A1 '^$number\.'`;
    if ($stuff =~ /(compute[\w]+)/) {
	my $node = $1;
	print "$jobid running on $node\n";
    } else {
	# Unparsable.  Default to whatever qsub had emitted
	print "$jobid\n";
    }
} else {
    # Unprasable, so just print whatever qsub had emitted
    print "$jobid\n";
}

unlink $jobfile;
