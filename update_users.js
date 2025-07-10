// Обновляем всех пользователей, у которых нет поля registeredAt
db.users.updateMany(
    { registeredAt: { $exists: false } },
    { $set: { registeredAt: new Date() } }
);

print("Users updated successfully!");
print("Updated users:");
db.users.find({}, {username: 1, registeredAt: 1}).forEach(print);