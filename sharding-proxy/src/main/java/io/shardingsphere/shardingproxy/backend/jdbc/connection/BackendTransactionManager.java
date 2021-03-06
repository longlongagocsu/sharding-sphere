/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package io.shardingsphere.shardingproxy.backend.jdbc.connection;

import com.google.common.base.Preconditions;
import io.shardingsphere.core.constant.transaction.TransactionOperationType;
import io.shardingsphere.core.constant.transaction.TransactionType;
import io.shardingsphere.core.event.transaction.ShardingTransactionEvent;
import io.shardingsphere.core.event.transaction.xa.XATransactionEvent;
import io.shardingsphere.spi.transaction.ShardingTransactionHandler;
import io.shardingsphere.spi.transaction.ShardingTransactionHandlerRegistry;
import lombok.RequiredArgsConstructor;

import java.sql.SQLException;

/**
 * Proxy transaction manager.
 *
 * @author zhaojun
 */
@RequiredArgsConstructor
public final class BackendTransactionManager implements TransactionManager {
    
    private final BackendConnection connection;
    
    @Override
    public void doInTransaction(final TransactionOperationType operationType) throws SQLException {
        TransactionType transactionType = connection.getTransactionType();
        ShardingTransactionHandler<ShardingTransactionEvent> shardingTransactionHandler = ShardingTransactionHandlerRegistry.getInstance().getHandler(transactionType);
        if (null != transactionType && transactionType != TransactionType.LOCAL) {
            Preconditions.checkNotNull(shardingTransactionHandler, String.format("Cannot find transaction manager of [%s]", transactionType));
        }
        if (TransactionOperationType.BEGIN == operationType && !connection.getStateHandler().isInTransaction()) {
            connection.getStateHandler().getAndSetStatus(ConnectionStatus.TRANSACTION);
            connection.releaseConnections(false);
        }
        if (TransactionType.LOCAL == transactionType) {
            new LocalTransactionManager(connection).doInTransaction(operationType);
        } else if (TransactionType.XA == transactionType) {
            shardingTransactionHandler.doInTransaction(new XATransactionEvent(operationType));
            if (TransactionOperationType.BEGIN != operationType) {
                connection.getStateHandler().getAndSetStatus(ConnectionStatus.TERMINATED);
            }
        }
    }
}
