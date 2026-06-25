using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace WfhDiary.Api.Migrations
{
    /// <inheritdoc />
    public partial class InitialCreate : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "diaries",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    OwnerUserId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    FyYear = table.Column<int>(type: "int", nullable: false),
                    FyStartDate = table.Column<DateOnly>(type: "date", nullable: false),
                    FyEndDate = table.Column<DateOnly>(type: "date", nullable: false),
                    CreatedAt = table.Column<DateTimeOffset>(type: "datetimeoffset", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_diaries", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "leave_periods",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    OwnerUserId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    StartDate = table.Column<DateOnly>(type: "date", nullable: false),
                    EndDate = table.Column<DateOnly>(type: "date", nullable: false),
                    Note = table.Column<string>(type: "nvarchar(max)", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_leave_periods", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "user_settings",
                columns: table => new
                {
                    OwnerUserId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    DisplayName = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    NotificationTimeMinutes = table.Column<int>(type: "int", nullable: false),
                    DefaultHomeStartMinutes = table.Column<int>(type: "int", nullable: false),
                    DefaultHomeFinishMinutes = table.Column<int>(type: "int", nullable: false),
                    DefaultHomeBreakHours = table.Column<decimal>(type: "decimal(18,2)", nullable: false),
                    DefaultOfficeStartMinutes = table.Column<int>(type: "int", nullable: false),
                    DefaultOfficeFinishMinutes = table.Column<int>(type: "int", nullable: false),
                    DefaultOfficeBreakHours = table.Column<decimal>(type: "decimal(18,2)", nullable: false),
                    NotificationWorkingDaysMask = table.Column<int>(type: "int", nullable: false),
                    MissedNotificationDefault = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    MissedNotificationApplyAtMinutes = table.Column<int>(type: "int", nullable: false),
                    MissedNotificationAwayDefault = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    GeolocationSkipNotificationAtHome = table.Column<bool>(type: "bit", nullable: false),
                    GeolocationSkipNotificationAtOffice = table.Column<bool>(type: "bit", nullable: false),
                    HomeGeofenceLatitude = table.Column<double>(type: "float", nullable: true),
                    HomeGeofenceLongitude = table.Column<double>(type: "float", nullable: true),
                    HomeGeofenceRadiusMeters = table.Column<int>(type: "int", nullable: true),
                    OfficeGeofenceLatitude = table.Column<double>(type: "float", nullable: true),
                    OfficeGeofenceLongitude = table.Column<double>(type: "float", nullable: true),
                    OfficeGeofenceRadiusMeters = table.Column<int>(type: "int", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_user_settings", x => x.OwnerUserId);
                });

            migrationBuilder.CreateTable(
                name: "work_templates",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    OwnerUserId = table.Column<string>(type: "nvarchar(450)", nullable: false),
                    Label = table.Column<string>(type: "nvarchar(max)", nullable: false),
                    SortOrder = table.Column<int>(type: "int", nullable: false),
                    StartTimeMinutes = table.Column<int>(type: "int", nullable: true),
                    FinishTimeMinutes = table.Column<int>(type: "int", nullable: true),
                    BreakHours = table.Column<decimal>(type: "decimal(18,2)", nullable: true),
                    Location = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    Description = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    IsDefault = table.Column<bool>(type: "bit", nullable: false),
                    GeofenceLatitude = table.Column<double>(type: "float", nullable: true),
                    GeofenceLongitude = table.Column<double>(type: "float", nullable: true),
                    GeofenceRadiusMeters = table.Column<int>(type: "int", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_work_templates", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "day_entries",
                columns: table => new
                {
                    Id = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    DiaryId = table.Column<Guid>(type: "uniqueidentifier", nullable: false),
                    Date = table.Column<DateOnly>(type: "date", nullable: false),
                    StartTimeMinutes = table.Column<int>(type: "int", nullable: true),
                    FinishTimeMinutes = table.Column<int>(type: "int", nullable: true),
                    BreakHours = table.Column<decimal>(type: "decimal(18,2)", nullable: true),
                    Description = table.Column<string>(type: "nvarchar(max)", nullable: true),
                    Location = table.Column<string>(type: "nvarchar(max)", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_day_entries", x => x.Id);
                    table.ForeignKey(
                        name: "FK_day_entries_diaries_DiaryId",
                        column: x => x.DiaryId,
                        principalTable: "diaries",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Cascade);
                });

            migrationBuilder.CreateIndex(
                name: "IX_day_entries_DiaryId_Date",
                table: "day_entries",
                columns: new[] { "DiaryId", "Date" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_diaries_OwnerUserId_FyYear",
                table: "diaries",
                columns: new[] { "OwnerUserId", "FyYear" },
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_leave_periods_OwnerUserId_StartDate_EndDate",
                table: "leave_periods",
                columns: new[] { "OwnerUserId", "StartDate", "EndDate" });

            migrationBuilder.CreateIndex(
                name: "IX_work_templates_OwnerUserId_SortOrder",
                table: "work_templates",
                columns: new[] { "OwnerUserId", "SortOrder" });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "day_entries");

            migrationBuilder.DropTable(
                name: "leave_periods");

            migrationBuilder.DropTable(
                name: "user_settings");

            migrationBuilder.DropTable(
                name: "work_templates");

            migrationBuilder.DropTable(
                name: "diaries");
        }
    }
}
