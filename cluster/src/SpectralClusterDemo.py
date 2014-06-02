from operator import concat
print(__doc__)

# Author: Kemal Eren <kemal@kemaleren.com>
# License: BSD 3 clause

# We only need to import this module
import os.path

import numpy as np
from matplotlib import pyplot as plt

from sklearn.datasets import make_biclusters
from sklearn.datasets import samples_generator as sg
from sklearn.cluster.bicluster import SpectralCoclustering
from sklearn.cluster.bicluster import SpectralBiclustering
from sklearn.metrics import consensus_score

# data, rows, columns = make_biclusters(
#      shape=(300, 300), n_clusters=5, noise=5,
#      shuffle=False, random_state=0)

# for i in range(len(data)):
#     print data[i]
# 
# print rows
# print columns


def plot (fname):
    f = open(fname)
    s= f.readlines()
    f.close()

    data = []

    for i in range(1, len(s)):
        line = s[i]
        if (not line.strip('\n')) :
            continue
        t= line.split('\t')
        tcopy = []
        nonzeros = 0 
        for j in range(1, len(t)):
            fl = float (t[j])
            tcopy.append(fl)
            if (fl != 0) :
                nonzeros = 1
        if (len(tcopy) >= 1 and nonzeros==1):
            data.append( tcopy )
        else :
            print 'zero line :' +i
    print "last line :" + s[len(s)-1]
    print "last data :" + str(len(data[-1]))
    
        
    for i in range(len(data)):
        for j in range(len(data[i])):
            if (data[i][j] != 0 and data[i][j] != 1) :
                print "i="+i + " j=" + j + " data=" + data[i][j]
# print rows
# print columns

#rows = range(len(data))
#columns = range(len(data[0]))

# exit()
    zeros = []
    for j in range(len(data[0])):
        nonzeros = 0 
        for i in range(len(data)):
            if (data[i][j]!=0):
                nonzeros =1
                break
        if (nonzeros == 0) :
            zeros.append(j)
            print "killing column "+ str(j)           
    
    
    for j in range(len(zeros)-1,-1,-1):
        for i in range(len(data)):
            del data[i][zeros[j]]
        print "killed column "+ str(zeros[j])  
        
    adata = np.array(data)


    fname = fname.split('/')[-2]
    
    try :
        nbclusters = int (fname.split('-')[-1]) * 2
        if (nbclusters < len(data)/5) :
            nbclusters = len(data)/3   
    except  :
        # len(data)/5
        nbclusters = len(data)/3  
    
#     plt.matshow(adata, cmap=plt.cm.Blues)
#     plt.title("Original "+fname)
    
#     adata, row_idx, col_idx = sg._shuffle(adata, random_state=0)
#     plt.matshow(data, cmap=plt.cm.Blues)
#     plt.title("Shuffled dataset")
 
    model = SpectralCoclustering(n_clusters=nbclusters, random_state=0)

    #for i in range(0,10):
    model.fit(adata)
 
    # score = consensus_score(model.biclusters_,
    #                         (rows[:, row_idx], columns[:, col_idx]))
    #   
    # print "consensus score: {:.3f}".format(score)
            
    
    fit_data = adata[np.argsort(model.row_labels_)]
    fit_data = fit_data[:, np.argsort(model.column_labels_)]
      
    plt.matshow(fit_data, cmap=plt.cm.Blues)
    plt.title("Coclustered ("+str(nbclusters) + ") "+ fname)
    
#     model = SpectralBiclustering(n_clusters=len(data)/5, random_state=0)
# 
#     #for i in range(0,10):
#     model.fit(adata)
#  
#     fit_data = adata[np.argsort(model.row_labels_)]
#     fit_data = fit_data[:, np.argsort(model.column_labels_)]
#       
#     plt.matshow(fit_data, cmap=plt.cm.Blues)
#     plt.title("After biclustering; rearranged to show biclusters")
    
    
        


topdir = "/data/thierry/SUMO/2014/workspace/MCC2014/"

def step(exten, dirname, names):
    
    for name in names:
        if name == "model.txt":
            print(os.path.join(dirname, name))
            plot(os.path.join(dirname, name))
 
# Start the walk
os.path.walk(topdir, step, "hi")

plt.show()
