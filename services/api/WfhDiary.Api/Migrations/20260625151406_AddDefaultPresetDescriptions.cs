using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace WfhDiary.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddDefaultPresetDescriptions : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<string>(
                name: "DefaultHomeDescription",
                table: "user_settings",
                type: "nvarchar(max)",
                nullable: true);

            migrationBuilder.AddColumn<string>(
                name: "DefaultOfficeDescription",
                table: "user_settings",
                type: "nvarchar(max)",
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "DefaultHomeDescription",
                table: "user_settings");

            migrationBuilder.DropColumn(
                name: "DefaultOfficeDescription",
                table: "user_settings");
        }
    }
}
