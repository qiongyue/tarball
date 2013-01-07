#!/usr/bin/perl -w

# tarball upload automatic script

use strict;
use File::Copy;
use File::Basename;

my $sourceDir = "/home/qiongyue/Documents/work/www/erp/Deployment/media/local/";
my @suffixlist = qw(.zip);
my $localDir = "/home/qiongyue/Documents/work/www/erp/Deployment/media/"; 
my $bakDir = $sourceDir . "backup/";
my $counter = 0;

mkdir($bakDir, 0755) unless(-d $bakDir);
while (<{$sourceDir}*.zip>) {
    my $absolutePath = $_;
    next unless (-f $absolutePath);
    
    my ($filename, $filePath, $suffix) = fileparse($absolutePath, @suffixlist);
    if (-f "$bakDir$filename$suffix") {
        copy($absolutePath, getAltName($bakDir, $filename, $suffix));
    } else {
        copy($absolutePath, "$bakDir$filename$suffix");
    }

    system("unzip $absolutePath -d $localDir/production");
    print "copy $absolutePath \n";

    unlink $absolutePath;

    $counter++;
}

if ($counter == 0) {
    print "no tarball was found...\n";
    exit(0);
}

print "start upload... \n";
$counter = 0;
my $msg = ""; 
my @numbers = qw(first secord third forth fifth);
while($msg = `pscp -r -v -sftp -pw your_password $localDir/production/ your_username\@yourdomain.com:/project_path/ 2>&1`) {
  if ($counter++ > 2) {
		print "upload fail...\n";
		last;
	}

	if ($msg =~ m/All channels closed/i) {
		print $msg;
		print "Success upload...\n";
        
		last;
	} else {
		#print "try...$counter\n";
		print "try the $numbers[$counter-1] time...\n";
		print $msg;
		sleep($counter);
	}
}

print "clear uploaded files...\n";
system("rm -r $localDir/production/*");


sub getAltName {
    my($dir, $filename, $suffix) = @_;
    my $counter = 0;
    while (<{$dir}{$filename}*{$suffix}>) {
        $counter++;
    }

    return sprintf("$dir$filename-%03d$suffix", $counter);
}
