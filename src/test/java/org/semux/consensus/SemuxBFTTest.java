/*
 * Copyright (c) 2017 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package org.semux.consensus;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semux.core.Block;
import org.semux.core.BlockHeader;
import org.semux.core.Blockchain;
import org.semux.core.BlockchainImpl;
import org.semux.core.Genesis;
import org.semux.core.PendingManager;
import org.semux.core.Transaction;
import org.semux.core.TransactionResult;
import org.semux.crypto.EdDSA;
import org.semux.crypto.EdDSA.Signature;
import org.semux.crypto.Hash;
import org.semux.db.MemoryDB;
import org.semux.net.ChannelManager;
import org.semux.utils.ByteArray;
import org.semux.utils.MerkleUtil;

public class SemuxBFTTest {

    private static SemuxBFT bft;
    private static EdDSA coinbase;

    @BeforeClass
    public static void setup() throws InterruptedException {
        Blockchain chain = new BlockchainImpl(MemoryDB.FACTORY);
        ChannelManager channelMgr = new ChannelManager();
        PendingManager pendingMgr = new PendingManager(chain, channelMgr);

        pendingMgr.start();

        bft = SemuxBFT.getInstance();
        coinbase = new EdDSA();
        bft.init(chain, channelMgr, pendingMgr, coinbase);

        new Thread(() -> {
            bft.start();
        }, "cons").start();

        Thread.sleep(200);
    }

    @Test
    public void testStart() {
        Assert.assertTrue(bft.isRunning());
    }

    @Test
    public void testVotesSerialization() {
        EdDSA key1 = new EdDSA();
        EdDSA key2 = new EdDSA();

        List<Transaction> transactions = new ArrayList<>();
        List<TransactionResult> results = new ArrayList<>();

        long number = 1;
        byte[] coinbase = key1.toAddress();
        byte[] prevHash = Genesis.getInstance().getHash();
        long timestamp = System.currentTimeMillis();
        byte[] transactionsRoot = MerkleUtil.computeTransactionsRoot(transactions);
        byte[] resultsRoot = MerkleUtil.computeResultsRoot(results);
        byte[] stateRoot = Hash.EMPTY_H256;
        byte[] data = {};
        int view = 1;

        BlockHeader header = new BlockHeader(number, coinbase, prevHash, timestamp, transactionsRoot, resultsRoot,
                stateRoot, data);
        Block block = new Block(header.sign(key1), transactions, results);

        List<Signature> votes = new ArrayList<>();
        Vote vote = new Vote(VoteType.PRECOMMIT, Vote.VALUE_APPROVE, block.getNumber(), view, block.getHash())
                .sign(key1);
        votes.add(vote.getSignature());
        vote = new Vote(VoteType.PRECOMMIT, Vote.VALUE_APPROVE, block.getNumber(), view, block.getHash()).sign(key2);
        votes.add(vote.getSignature());

        block.setView(view);
        block.setVotes(votes);
        block = Block.fromBytes(block.toBytes());

        assertTrue(block.validate());
        for (Signature sig : block.getVotes()) {
            ByteArray addr = ByteArray.of(Hash.h160(sig.getPublicKey()));

            assertTrue(addr.equals(ByteArray.of(key1.toAddress())) || addr.equals(ByteArray.of(key2.toAddress())));
            assertTrue(EdDSA.verify(vote.getEncoded(), sig));
        }
    }

    @AfterClass
    public static void teardown() throws InterruptedException {
        bft.stop();

        Thread.sleep(200);
    }
}
